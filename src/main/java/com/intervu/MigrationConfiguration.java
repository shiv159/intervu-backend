package com.intervu;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
class MigrationConfiguration {

	@Bean(initMethod = "migrate")
	@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
	Flyway flyway(DataSource dataSource) {
		return Flyway.configure().dataSource(dataSource).load();
	}
}
