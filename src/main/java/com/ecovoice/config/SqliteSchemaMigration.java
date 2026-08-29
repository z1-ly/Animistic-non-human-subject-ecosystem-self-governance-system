package com.ecovoice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SQLite 由 Hibernate 首次建表时会写入 enum CHECK 约束，后续新增枚举值不会自动更新约束。
 * 此迁移在启动时移除过期的 CHECK 约束，避免 NODE_JOIN / BOUNTY_CLAIM 等写入失败。
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class SqliteSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            migrateLedgerTransactionsIfNeeded();
            migrateNatureAppealsIfNeeded();
        } catch (Exception e) {
            log.warn("Schema migration skipped or failed: {}", e.getMessage());
        }
    }

    private String tableDdl(String table) {
        List<String> rows = jdbcTemplate.query(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                (rs, rowNum) -> rs.getString(1),
                table);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> tableColumns(String table) {
        return jdbcTemplate.query(
                "PRAGMA table_info(" + table + ")",
                (rs, rowNum) -> rs.getString("name"));
    }

    private void migrateLedgerTransactionsIfNeeded() {
        String ddl = tableDdl("ledger_transactions");
        if (ddl == null || !ddl.toUpperCase().contains("CHECK") || ddl.contains("NODE_JOIN")) {
            return;
        }
        log.info("Migrating ledger_transactions: expanding allowed transaction types");
        recreateTableWithoutChecks("ledger_transactions", """
                CREATE TABLE ledger_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    block_id INTEGER NOT NULL,
                    tx_hash VARCHAR(64) NOT NULL,
                    type VARCHAR(30) NOT NULL,
                    entity_id INTEGER,
                    appeal_id INTEGER,
                    from_address VARCHAR(66),
                    to_address VARCHAR(66),
                    amount REAL NOT NULL,
                    payload VARCHAR(500),
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE (tx_hash)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_tx_entity ON ledger_transactions(entity_id)");
    }

    private void migrateNatureAppealsIfNeeded() {
        String ddl = tableDdl("nature_appeals");
        if (ddl == null || !ddl.toUpperCase().contains("CHECK") || ddl.contains("CLAIMED")) {
            return;
        }
        log.info("Migrating nature_appeals: expanding bounty_status values");
        recreateTableWithoutChecks("nature_appeals", """
                CREATE TABLE nature_appeals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id INTEGER NOT NULL,
                    appeal_type VARCHAR(30) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    trigger_rule VARCHAR(200),
                    severity VARCHAR(20),
                    action_detail VARCHAR(500),
                    measurable_target VARCHAR(200),
                    deadline TIMESTAMP,
                    bounty_amount REAL,
                    bounty_status VARCHAR(20),
                    contract_address VARCHAR(66),
                    contract_tx_hash VARCHAR(64),
                    fulfiller_name VARCHAR(80),
                    claimant_node_id INTEGER,
                    claimed_at TIMESTAMP,
                    fulfilled_at TIMESTAMP,
                    active BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_appeal_entity_active ON nature_appeals(entity_id, appeal_type, active)");
    }

    private void recreateTableWithoutChecks(String table, String createSql) {
        List<String> columns = tableColumns(table);
        if (columns.isEmpty()) {
            return;
        }
        String colList = String.join(", ", columns);
        String temp = table + "_mig";

        jdbcTemplate.execute("PRAGMA foreign_keys=OFF");
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + temp);
            jdbcTemplate.execute(createSql.replace("CREATE TABLE " + table, "CREATE TABLE " + temp));
            jdbcTemplate.execute("INSERT INTO " + temp + " (" + colList + ") SELECT " + colList + " FROM " + table);
            jdbcTemplate.execute("DROP TABLE " + table);
            jdbcTemplate.execute("ALTER TABLE " + temp + " RENAME TO " + table);
            log.info("Table {} migrated successfully", table);
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        }
    }
}
