package net.lab1024.sa.admin.module.scm.common;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class ScmProductMigrationIT {
    @Test
    void migrationsApplyAndValidateWithoutChangingLegacySchema() throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2";
        String user = System.getenv().getOrDefault("XSY_V2_DB_USERNAME", "xsy_scm_app");
        String password = System.getenv("XSY_V2_DB_PASSWORD");
        Flyway flyway = Flyway.configure().dataSource(url,user,password)
                .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true)
                .placeholderReplacement(false).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();
        try (var connection = DriverManager.getConnection(url,user,password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('6','7')")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy_v2' AND table_name IN ('product_category','product_spu','product_sku','product_image')")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }
}
