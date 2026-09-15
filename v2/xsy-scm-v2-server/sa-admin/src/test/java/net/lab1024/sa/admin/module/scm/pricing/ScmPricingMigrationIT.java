package net.lab1024.sa.admin.module.scm.pricing;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class ScmPricingMigrationIT extends ScmW3PgITBase {
    @Autowired Flyway flyway;
    @Test void validatesMigrationsAndPricingSchema() {
        flyway.validate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('10','11') AND success", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('customer_agreement_price','customer_type_price','customer_agreement_price_operation_log','customer_type_price_operation_log','customer_price_batch_audit','customer_sku_visibility')", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='product_sku' AND column_name='market_price'", String.class)).isEqualTo("NO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='x'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id IN (425,435,486,487,501,502,503,504,505,506,511,512,513,514,521,522,523,524,525,531,541)", Integer.class)).isEqualTo(21);
    }
}
