package com.intervu;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MigrationConfigurationTests {

	@Test
	void configuresFlywayFromTheApplicationDataSource() {
		var flyway = new MigrationConfiguration().flyway(mock(DataSource.class));

		assertThat(flyway).isNotNull();
	}
}
