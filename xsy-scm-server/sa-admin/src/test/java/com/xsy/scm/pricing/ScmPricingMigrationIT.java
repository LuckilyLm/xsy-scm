package com.xsy.scm.pricing;

import com.xsy.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.test.PgITMigrationHistory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ScmPricingMigrationIT extends ScmW3PgITBase {
    @Autowired
    Flyway flyway;

    @Test
    void validatesMigrationsAndPricingSchema() throws Exception {
        flyway.validate();
        try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
            PgITMigrationHistory.assertCovered(statement, "10", "11");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('xsy_customer_agreement_price','xsy_price_operation_log','xsy_customer_sku_visibility')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='xsy_product_sku' AND column_name='market_price'", String.class)).isEqualTo("NO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='x'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id IN (425,435,486,487,501,502,503,504,505,506,511,512,513,514,521,522,523,524,525,531,541)", Integer.class)).isEqualTo(21);
    }
}
