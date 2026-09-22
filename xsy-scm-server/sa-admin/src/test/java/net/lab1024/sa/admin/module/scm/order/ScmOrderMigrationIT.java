package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ScmOrderMigrationIT extends ScmW3PgITBase {
    @Autowired
    Flyway flyway;
    static final String TABLES = "'sales_order','sales_order_item','order_address_snapshot','order_operation_log','idempotency_record','order_return','order_return_item','order_refund'";

    @Test
    void approvedSchemaHasEightTablesNoDeadFieldsAndNullableDraftPrices() {
        flyway.validate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ")", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename IN (" + TABLES + ") AND indexname NOT LIKE '%_pkey'", Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.sequences WHERE sequence_schema=current_schema() AND sequence_name IN ('sales_order_no_seq','order_return_no_seq','order_refund_no_seq')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND column_name IN ('fulfillment_status','pay_status','actual_weight')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('sales_order','sales_order_item') AND column_name IN ('draft_unit_price','draft_price_source','ordered_line_amount','ordered_total_amount') AND is_nullable='YES'", Integer.class)).isEqualTo(4);
        // 精度规则按 (表,列) 排除坐标列：按列名全局排除会让同名列在任何订单表上逃过金额精度校验
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND data_type='numeric' AND (table_name,column_name) NOT IN (('order_address_snapshot','longitude'),('order_address_snapshot','latitude')) AND (numeric_precision<>18 OR numeric_scale<>4)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='order_address_snapshot' AND data_type='numeric' AND (column_name,numeric_precision,numeric_scale) IN (('longitude',11,8),('latitude',10,8))", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f' AND conrelid IN (SELECT oid FROM pg_class WHERE relname IN (" + TABLES + "))", Integer.class)).isZero();
    }

    @Test
    void permissionMenusGrantedAndBusinessLogsHaveNoMutationMapper() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642)", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu WHERE role_id=1 AND menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642)", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_sales_order_source'", String.class)).contains("IMPORT");
        assertThat(net.lab1024.sa.admin.module.scm.order.dao.OrderOperationLogDao.class.getMethods()).extracting(java.lang.reflect.Method::getName).containsExactlyInAnyOrder("insert", "query");
    }
}
