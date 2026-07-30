package com.autobanrobot.server.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseCompatibilityConfig {

    @Bean
    HibernatePropertiesCustomizer legacyMySqlDialect(DataSource dataSource) {
        return properties -> {
            String jdbcUrl = jdbcUrl(dataSource);
            if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:")) {
                properties.put(
                    "hibernate.dialect",
                    "org.hibernate.community.dialect.MySQLLegacyDialect"
                );
            }
        };
    }

    private String jdbcUrl(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource.getJdbcUrl();
        }
        return null;
    }
}
