package com.xianshuyuan.scm.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSixTablesWithoutForeignKeys() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'sys_user', 'supplier', 'warehouse',
                'product_category', 'product_spu', 'product_sku'
              )
            """, Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = 'public'
              and constraint_type = 'FOREIGN KEY'
            """, Integer.class);

        assertThat(tables).isEqualTo(6);
        assertThat(foreignKeys).isZero();
    }

    @Test
    void createsRequiredPartialUniqueIndexes() {
        Integer indexes = jdbcTemplate.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'uk_product_spu_code_active',
                'uk_product_sku_code_active',
                'uk_product_sku_barcode_active',
                'uk_product_sku_default_active'
              )
              and indexdef ilike '% where %'
            """, Integer.class);

        assertThat(indexes).isEqualTo(4);
    }

    @Test
    void usesJsonbAndNumericForSkuSpecificationsAndPrice() {
        String specType = jdbcTemplate.queryForObject("""
            select data_type from information_schema.columns
            where table_schema = 'public' and table_name = 'product_sku'
              and column_name = 'spec_values'
            """, String.class);
        String priceType = jdbcTemplate.queryForObject("""
            select data_type from information_schema.columns
            where table_schema = 'public' and table_name = 'product_sku'
              and column_name = 'market_price'
            """, String.class);

        assertThat(specType).isEqualTo("jsonb");
        assertThat(priceType).isEqualTo("numeric");
    }

    @Test
    void seedsThreeLevelCategoryAndProductWithOneDefaultSku() {
        Integer categoryLevels = jdbcTemplate.queryForObject("""
            select count(distinct level) from product_category where deleted = false
            """, Integer.class);
        Integer activeSkus = jdbcTemplate.queryForObject("""
            select count(*) from product_sku where deleted = false
            """, Integer.class);
        Integer defaultSkus = jdbcTemplate.queryForObject("""
            select count(*) from product_sku where deleted = false and is_default = true
            """, Integer.class);

        assertThat(categoryLevels).isEqualTo(3);
        assertThat(activeSkus).isEqualTo(2);
        assertThat(defaultSkus).isEqualTo(1);
    }

    @Test
    void createsSprintTwoTablesWithoutForeignKeys() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'customer_type', 'customer', 'customer_sku_visibility',
                'customer_agreement_price', 'sales_order', 'sales_order_item',
                'order_operation_log', 'idempotency_record', 'order_return',
                'order_return_item', 'order_refund'
              )
            """, Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = 'public'
              and constraint_type = 'FOREIGN KEY'
            """, Integer.class);

        assertThat(tables).isEqualTo(11);
        assertThat(foreignKeys).isZero();
    }

    @Test
    void createsSprintTwoPartialUniqueAndQueryIndexes() {
        Integer partialUniqueIndexes = jdbcTemplate.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'uk_customer_type_code_active', 'uk_customer_code_active',
                'uk_customer_sku_visibility_active',
                'uk_sales_order_no_active', 'uk_order_return_no_active',
                'uk_order_refund_no_active', 'uk_order_refund_return_active',
                'uk_order_refund_external_reference_active'
              )
              and indexdef ilike '% where %'
            """, Integer.class);
        Integer queryIndexes = jdbcTemplate.queryForObject("""
            select count(*) from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'idx_customer_agreement_price_lookup',
                'idx_sales_order_customer_created',
                'idx_sales_order_item_order_id',
                'idx_order_operation_log_order_created',
                'idx_order_return_order_id',
                'idx_order_return_item_return_id'
              )
            """, Integer.class);

        assertThat(partialUniqueIndexes).isEqualTo(8);
        assertThat(queryIndexes).isEqualTo(6);
    }

    @Test
    void usesRequiredSprintTwoNumericAndJsonbColumns() {
        Integer numericColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and data_type = 'numeric'
              and numeric_precision = 18
              and numeric_scale = 4
              and (table_name, column_name) in (
                ('customer_agreement_price', 'unit_price'),
                ('sales_order', 'ordered_total_amount'),
                ('sales_order', 'settlement_total_amount'),
                ('sales_order_item', 'ordered_quantity'),
                ('sales_order_item', 'actual_quantity'),
                ('sales_order_item', 'locked_unit_price'),
                ('sales_order_item', 'ordered_line_amount'),
                ('sales_order_item', 'settlement_line_amount'),
                ('order_return', 'approved_amount'),
                ('order_return_item', 'requested_quantity'),
                ('order_return_item', 'approved_quantity'),
                ('order_return_item', 'approved_amount'),
                ('order_refund', 'refund_amount')
              )
            """, Integer.class);
        Integer jsonbColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public' and data_type = 'jsonb'
              and (table_name, column_name) in (
                ('sales_order_item', 'spec_values_snapshot'),
                ('order_operation_log', 'before_data'),
                ('order_operation_log', 'after_data'),
                ('idempotency_record', 'result_data')
              )
            """, Integer.class);

        assertThat(numericColumns).isEqualTo(13);
        assertThat(jsonbColumns).isEqualTo(4);
    }

    @Test
    void createsDocumentNumberSequencesAndStableDemoCustomer() {
        Integer sequences = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.sequences
            where sequence_schema = 'public'
              and sequence_name in (
                'sales_order_no_seq', 'order_return_no_seq', 'order_refund_no_seq'
              )
            """, Integer.class);
        Integer demoCustomers = jdbcTemplate.queryForObject("""
            select count(*) from customer
            where customer_code = 'CUST-DEMO-001'
              and name = '鲜蔬源演示客户'
              and status = 'ENABLED'
              and deleted = false
            """, Integer.class);

        assertThat(sequences).isEqualTo(3);
        assertThat(demoCustomers).isEqualTo(1);
    }
}
