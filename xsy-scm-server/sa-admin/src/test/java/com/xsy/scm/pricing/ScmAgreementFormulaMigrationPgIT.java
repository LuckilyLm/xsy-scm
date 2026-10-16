package com.xsy.scm.pricing;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实 PostgreSQL V139 协议价公式字段、默认值与约束；每项事务自动回滚。 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class ScmAgreementFormulaMigrationPgIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void legacyFixedPriceKeepsDefaultFormulaType() {
        jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, effective_from) "
                + "VALUES (91110, 90001, 80001, 8.5000, now())");
        assertThat(jdbc.queryForObject("SELECT formula_type FROM xsy_customer_agreement_price "
                + "WHERE id=91110", String.class)).isEqualTo("FIXED");
    }

    @Test
    void costMarkupAndMarketMarkupPermitNullFixedPrice() {
        jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                + "VALUES (91111, 90001, 80002, null, 'COST_MARKUP_RATE', 0.2000, now())");
        jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                + "VALUES (91112, 90001, 80003, null, 'MARKET_MARKUP_RATE', 0.1000, now())");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_customer_agreement_price WHERE id IN (91111,91112)"
                + " AND unit_price IS NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidNegativeMarkupIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                + "VALUES (91113, 90001, 80004, null, 'COST_MARKUP_RATE', -0.1000, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fixedPriceCannotCarryAnExtraFormulaParameter() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                + "VALUES (91114, 90001, 80005, 10.0000, 'FIXED', 1.0000, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formulaPriceCannotAlsoSupplyFixedPrice() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO xsy_customer_agreement_price "
                + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                + "VALUES (91115, 90001, 80006, 10.0000, 'MARKET_PLUS_AMOUNT', 2.0000, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
