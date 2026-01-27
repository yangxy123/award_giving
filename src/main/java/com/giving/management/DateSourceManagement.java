package com.giving.management;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zzby
 * @version 创建时间： 2026/1/26 下午2:07
 */
@Primary
@Component
@Data
public class DateSourceManagement extends AbstractRoutingDataSource {
    @Qualifier("gc")
    @Autowired
    private DataSource gc;
    @Qualifier("gs")
    @Autowired
    private DataSource gs;

    public static ThreadLocal<String> flag = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        try {
            System.out.println("===============" + gc.getConnection().toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Map<Object,Object> targetDataSource = new ConcurrentHashMap<>();
        targetDataSource.put("gc", gc);
        targetDataSource.put("gs", gs);

        this.setTargetDataSources(targetDataSource);
        this.setDefaultTargetDataSource(gc);
        this.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return flag.get();
    }
}
