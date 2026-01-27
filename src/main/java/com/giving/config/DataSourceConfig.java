package com.giving.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * @author zzby
 * @version 创建时间： 2026/1/26 下午2:04
 */
@Configuration
public class DataSourceConfig {
    @Bean(name = "gc")
    @ConfigurationProperties(prefix = "spring.datasource.gc")
    public DataSource dataSource1() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "gs")
    @ConfigurationProperties(prefix = "spring.datasource.gs")
    public DataSource dataSource2() {
        return DataSourceBuilder.create().build();
    }
}
