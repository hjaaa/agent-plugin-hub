package com.agentpluginhub.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void flyway_should_create_all_core_tables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('plugin','plugin_version','dist_tag','submission','app_user','user_role','registry_token')",
                Integer.class);
        assertThat(count).isEqualTo(7);
    }
}
