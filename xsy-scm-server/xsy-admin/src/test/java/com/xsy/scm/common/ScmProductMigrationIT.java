package com.xsy.scm.common;

import com.xsy.scm.admin.test.PgITDatabase;
import com.xsy.scm.admin.test.PgITMigrationHistory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ScmProductMigrationIT {
    @Test
    void migrationsApplyAndValidateWithoutChangingLegacySchema() throws Exception {
        String url = PgITDatabase.url();
        String user = PgITDatabase.user();
        String password = PgITDatabase.password();
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true)
                .placeholderReplacement(false)
                .locations("classpath:db/migration/modules").load();
        flyway.migrate();
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            PgITMigrationHistory.assertCovered(statement, "6", "7");
            try (var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy_v2' AND table_name IN ('xsy_product_category','xsy_product_spu','xsy_product_sku','xsy_product_image')")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }
}
