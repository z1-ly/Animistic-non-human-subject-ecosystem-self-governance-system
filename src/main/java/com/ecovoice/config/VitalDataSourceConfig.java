package com.ecovoice.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class VitalDataSourceConfig {

    @Bean(name = "vitalsJdbcTemplate")
    public JdbcTemplate vitalsJdbcTemplate() {
        DataSource dataSource = DataSourceBuilder.create()
                .url("jdbc:mysql://localhost:3306/ecovoice_vitals?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false")
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .username("root")
                .password("123456")
                .build();
        
        return new JdbcTemplate(dataSource);
    }
}
