package com.ecovoice.service;

import com.ecovoice.domain.ProtocolType;
import com.ecovoice.domain.SensorType;
import com.ecovoice.domain.VitalSignRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VitalRecordService {

    private final JdbcTemplate vitalsJdbc;

    public VitalRecordService(@Qualifier("vitalsJdbcTemplate") JdbcTemplate vitalsJdbc) {
        this.vitalsJdbc = vitalsJdbc;
        initTable();
    }

    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS vital_sign_records (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                entity_id BIGINT NOT NULL,
                node_id BIGINT NOT NULL,
                sensor_type VARCHAR(50) NOT NULL,
                value DOUBLE NOT NULL,
                protocol VARCHAR(20),
                recorded_at TIMESTAMP NOT NULL
            )
            """;
        try {
            vitalsJdbc.execute(sql);
            System.out.println("=== vital_sign_records 表创建成功 ===");
        } catch (Exception e) {
            System.err.println("=== vital_sign_records 表创建失败: " + e.getMessage() + " ===");
            e.printStackTrace();
        }
    }

    public VitalSignRecord save(VitalSignRecord record) {
        String sql = """
            INSERT INTO vital_sign_records (entity_id, node_id, sensor_type, value, protocol, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        vitalsJdbc.update(sql,
            record.getEntityId(),
            record.getNodeId(),
            record.getSensorType().name(),
            record.getValue(),
            record.getProtocol() != null ? record.getProtocol().name() : null,
            Timestamp.valueOf(record.getRecordedAt())
        );
        // 获取自增ID
        Long id = vitalsJdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        record.setId(id);
        return record;
    }

    public List<VitalSignRecord> findByEntityIdOrderByRecordedAtDesc(Long entityId, int limit) {
        if (entityId == null) {
            String sql = """
                SELECT * FROM vital_sign_records 
                ORDER BY recorded_at DESC 
                LIMIT ?
                """;
            return vitalsJdbc.query(sql, new VitalRecordMapper(), limit);
        }
        String sql = """
            SELECT * FROM vital_sign_records 
            WHERE entity_id = ? 
            ORDER BY recorded_at DESC 
            LIMIT ?
            """;
        return vitalsJdbc.query(sql, new VitalRecordMapper(), entityId, limit);
    }

    public List<VitalSignRecord> findByEntityIdAndSensorTypeAndRecordedAtAfter(
            Long entityId, SensorType sensorType, LocalDateTime after) {
        String sql = """
            SELECT * FROM vital_sign_records 
            WHERE entity_id = ? AND sensor_type = ? AND recorded_at > ?
            ORDER BY recorded_at ASC
            """;
        return vitalsJdbc.query(sql, new VitalRecordMapper(), entityId, sensorType.name(), Timestamp.valueOf(after));
    }

    public List<VitalSignRecord> findByEntityIdAndSensorTypeAndRecordedAtBetween(
            Long entityId, SensorType sensorType, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT * FROM vital_sign_records 
            WHERE entity_id = ? AND sensor_type = ? AND recorded_at BETWEEN ? AND ?
            ORDER BY recorded_at ASC
            """;
        return vitalsJdbc.query(sql, new VitalRecordMapper(), 
            entityId, sensorType.name(), Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    public List<VitalSignRecord> findByEntityIdAndRecordedAtAfter(Long entityId, LocalDateTime after) {
        String sql = """
            SELECT * FROM vital_sign_records 
            WHERE entity_id = ? AND recorded_at > ?
            ORDER BY recorded_at ASC
            """;
        return vitalsJdbc.query(sql, new VitalRecordMapper(), entityId, Timestamp.valueOf(after));
    }

    public long count() {
        return vitalsJdbc.queryForObject("SELECT COUNT(*) FROM vital_sign_records", Long.class);
    }

    public long countByEntity(Long entityId) {
        return vitalsJdbc.queryForObject(
            "SELECT COUNT(*) FROM vital_sign_records WHERE entity_id = ?", Long.class, entityId);
    }

    private static class VitalRecordMapper implements RowMapper<VitalSignRecord> {
        @Override
        public VitalSignRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            VitalSignRecord record = new VitalSignRecord();
            record.setId(rs.getLong("id"));
            record.setEntityId(rs.getLong("entity_id"));
            record.setNodeId(rs.getLong("node_id"));
            record.setSensorType(SensorType.valueOf(rs.getString("sensor_type")));
            record.setValue(rs.getDouble("value"));
            String protocolStr = rs.getString("protocol");
            if (protocolStr != null) {
                record.setProtocol(ProtocolType.valueOf(protocolStr));
            }
            Timestamp ts = rs.getTimestamp("recorded_at");
            if (ts != null) {
                record.setRecordedAt(ts.toLocalDateTime());
            }
            return record;
        }
    }
}
