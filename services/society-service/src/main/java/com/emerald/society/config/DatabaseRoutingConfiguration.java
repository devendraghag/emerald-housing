package com.emerald.society.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration(proxyBeanMethods = false)
public class DatabaseRoutingConfiguration {
    @Bean("writerDataSource")
    @ConfigurationProperties("app.datasource.writer")
    HikariDataSource writerDataSource() {
        return new HikariDataSource();
    }

    @Bean("readerDataSource")
    @ConditionalOnProperty(prefix = "app.datasource.reader", name = "enabled", havingValue = "true")
    @ConfigurationProperties("app.datasource.reader")
    HikariDataSource readerDataSource() {
        return new HikariDataSource();
    }

    @Bean("dataSource")
    @Primary
    DataSource dataSource(@Qualifier("writerDataSource") DataSource writer,
            @Qualifier("readerDataSource") ObjectProvider<DataSource> readerProvider) {
        DataSource reader = readerProvider.getIfAvailable(() -> writer);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(ReadOnlyRoutingDataSource.READ_ONLY, reader);

        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(writer);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routing);
    }
}
