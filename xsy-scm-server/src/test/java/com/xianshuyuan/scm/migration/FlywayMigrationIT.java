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
                'order_return_item', 'order_refund',
                'customer_agreement_price_operation_log'
              )
            """, Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = 'public'
              and constraint_type = 'FOREIGN KEY'
            """, Integer.class);

        assertThat(tables).isEqualTo(12);
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
                'idx_order_return_item_return_id',
                'idx_agreement_price_log_price_created'
              )
            """, Integer.class);

        assertThat(partialUniqueIndexes).isEqualTo(8);
        assertThat(queryIndexes).isEqualTo(7);
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
                ('idempotency_record', 'result_data'),
                ('customer_agreement_price_operation_log', 'before_data'),
                ('customer_agreement_price_operation_log', 'after_data')
              )
            """, Integer.class);

        assertThat(numericColumns).isEqualTo(13);
        assertThat(jsonbColumns).isEqualTo(6);
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

    @Test
    void createsSprintThreePurchaseReceivingAndInventoryTablesWithoutForeignKeys() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'supplier_sku', 'purchase_demand', 'purchase_demand_allocation',
                'purchase_order', 'purchase_order_item', 'purchase_operation_log',
                'purchase_receipt', 'purchase_receipt_item', 'receipt_weighing_record',
                'inventory', 'inventory_movement'
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
    void evolvesSupplierAndWarehouseAndCreatesSprintThreeDocumentSequences() {
        Integer evolvedColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and (table_name, column_name) in (
                ('supplier', 'remark'),
                ('warehouse', 'address'),
                ('warehouse', 'remark')
              )
            """, Integer.class);
        Integer sequences = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.sequences
            where sequence_schema = 'public'
              and sequence_name in (
                'purchase_order_no_seq', 'purchase_receipt_no_seq',
                'inventory_movement_no_seq'
              )
            """, Integer.class);

        assertThat(evolvedColumns).isEqualTo(3);
        assertThat(sequences).isEqualTo(3);
    }

    @Test
    void createsSprintThreeActiveAndSourceUniquenessIndexes() {
        Integer partialUniqueIndexes = jdbcTemplate.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'uk_supplier_sku_active', 'uk_purchase_demand_source_active',
                'uk_purchase_order_no_active', 'uk_purchase_receipt_no_active',
                'uk_inventory_warehouse_sku_active'
              )
              and indexdef ilike '% where %'
            """, Integer.class);
        Integer sourceUniqueIndexes = jdbcTemplate.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'uk_purchase_demand_allocation_source_active',
                'uk_purchase_receipt_order_active',
                'uk_purchase_receipt_confirmation_key',
                'uk_inventory_movement_receipt_confirmation'
              )
              and indexdef ilike 'create unique index%'
            """, Integer.class);

        assertThat(partialUniqueIndexes).isEqualTo(5);
        assertThat(sourceUniqueIndexes).isEqualTo(4);

        Integer queryIndexes = jdbcTemplate.queryForObject("""
            select count(*) from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'idx_supplier_sku_sku_id', 'idx_purchase_demand_sku_status',
                'idx_purchase_order_status_supplier_warehouse', 'idx_purchase_order_item_order_id',
                'idx_purchase_demand_allocation_demand_id', 'idx_purchase_receipt_order_status',
                'idx_purchase_receipt_item_receipt_id', 'idx_receipt_weighing_record_item_recorded',
                'idx_inventory_sku_id', 'idx_inventory_movement_warehouse_sku_occurred',
                'idx_inventory_movement_source'
              )
            """, Integer.class);
        assertThat(queryIndexes).isEqualTo(11);
    }

    @Test
    void validatesSprintThreeIndexKeysAndPredicates() {
        String demandIndex = jdbcTemplate.queryForObject("""
            select indexdef from pg_indexes
            where schemaname = 'public' and indexname = 'uk_purchase_demand_source_active'
            """, String.class);
        String inventoryIndex = indexDefinition("uk_inventory_warehouse_sku_active");
        String supplierSkuIndex = indexDefinition("uk_supplier_sku_active");
        String purchaseOrderIndex = indexDefinition("uk_purchase_order_no_active");
        String receiptIndex = indexDefinition("uk_purchase_receipt_no_active");
        String allocationIndex = indexDefinition("uk_purchase_demand_allocation_source_active");
        String receiptItemIndex = indexDefinition("uk_purchase_receipt_item_order_item_active");
        String movementIndex = indexDefinition("uk_inventory_movement_receipt_confirmation");

        assertThat(demandIndex.toLowerCase()).contains("(sales_order_item_id)", "where (deleted = false)");
        assertThat(inventoryIndex.toLowerCase()).contains("(warehouse_id, sku_id)", "where (deleted = false)");
        assertThat(supplierSkuIndex.toLowerCase()).contains("(supplier_id, sku_id)", "where (deleted = false)");
        assertThat(purchaseOrderIndex.toLowerCase()).contains("(order_no)", "where (deleted = false)");
        assertThat(receiptIndex.toLowerCase()).contains("(receipt_no)", "where (deleted = false)");
        assertThat(allocationIndex.toLowerCase())
            .contains("(purchase_order_item_id, purchase_demand_id)", "where (deleted = false)");
        assertThat(receiptItemIndex.toLowerCase())
            .contains("(purchase_receipt_id, purchase_order_item_id)", "where (deleted = false)");
        assertThat(movementIndex.toLowerCase())
            .contains("(source_document_type, source_document_id, source_document_item_id, confirmation_id)",
                "deleted = false", "movement_type", "purchase_in");
    }

    @Test
    void enforcesSprintThreeReceiptAndMovementSemantics() {
        String receiptWeightSource = constraintDefinition("ck_purchase_receipt_item_weight_source");
        String weighingSource = constraintDefinition("ck_receipt_weighing_record_source");
        String receiptWeightFields = constraintDefinition("ck_purchase_receipt_item_weight_fields");
        String movementQuantities = constraintDefinition("ck_inventory_movement_quantities");
        String movementQuantityChange = constraintDefinition("ck_inventory_movement_quantity_change");
        String movementSourcePair = constraintDefinition("ck_inventory_movement_source_pair");

        assertThat(receiptWeightSource).contains("MANUAL").doesNotContain("DEVICE");
        assertThat(weighingSource).contains("MANUAL").doesNotContain("DEVICE");
        assertThat(receiptWeightFields).contains("actual_weight").contains("weight_unit");
        assertThat(movementQuantities.toLowerCase())
            .contains("quantity_after", "quantity_before + quantity_change")
            .doesNotContain("quantity_before >=", "quantity_after >=");
        assertThat(movementQuantityChange.toLowerCase()).contains("quantity_change", "<>", "0");
        assertThat(movementSourcePair.toLowerCase())
            .contains("movement_type", "purchase_in", "source_document_type", "purchase_receipt");

        String receivedNullable = jdbcTemplate.queryForObject("""
            select is_nullable from information_schema.columns
            where table_schema = 'public' and table_name = 'purchase_receipt_item'
              and column_name = 'received_quantity'
            """, String.class);
        String actualWeightNullable = jdbcTemplate.queryForObject("""
            select is_nullable from information_schema.columns
            where table_schema = 'public' and table_name = 'purchase_receipt_item'
              and column_name = 'actual_weight'
            """, String.class);
        assertThat(receivedNullable).isEqualTo("NO");
        assertThat(actualWeightNullable).isEqualTo("YES");
    }

    @Test
    void enforcesOneActiveReceiptPerPurchaseOrder() {
        String receiptOrderIndex = indexDefinition("uk_purchase_receipt_order_active");

        assertThat(receiptOrderIndex.toLowerCase())
            .contains("unique", "(purchase_order_id)", "where (deleted = false)");
    }

    @Test
    void givesPurchaseOperationLogStandardBusinessAuditColumns() {
        Integer columns = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.columns
            where table_schema = 'public' and table_name = 'purchase_operation_log'
              and column_name in ('version', 'deleted', 'updated_at', 'updated_by')
            """, Integer.class);

        assertThat(columns).isEqualTo(4);
        assertThat(constraintDefinition("ck_purchase_operation_log_version").toLowerCase())
            .contains("version >= 0");
    }

    @Test
    void makesSprintThreeDocumentSequencesUsable() {
        Long purchaseOrderNumber = jdbcTemplate.queryForObject("select nextval('purchase_order_no_seq')", Long.class);
        Long receiptNumber = jdbcTemplate.queryForObject("select nextval('purchase_receipt_no_seq')", Long.class);
        Long movementNumber = jdbcTemplate.queryForObject("select nextval('inventory_movement_no_seq')", Long.class);

        assertThat(purchaseOrderNumber).isPositive();
        assertThat(receiptNumber).isPositive();
        assertThat(movementNumber).isPositive();
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject("""
            select indexdef from pg_indexes
            where schemaname = 'public' and indexname = ?
            """, String.class, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject("""
            select pg_get_constraintdef(oid)
            from pg_constraint
            where connamespace = 'public'::regnamespace and conname = ?
            """, String.class, constraintName);
    }

    @Test
    void usesSprintThreeNumericAndJsonbSnapshotColumns() {
        Integer numericColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and data_type = 'numeric'
              and numeric_precision = 18
              and numeric_scale = 4
              and (table_name, column_name) in (
                ('purchase_demand', 'required_quantity'),
                ('purchase_demand', 'allocated_quantity'),
                ('purchase_demand', 'fulfilled_quantity'),
                ('purchase_order', 'total_amount'),
                ('purchase_order_item', 'planned_quantity'),
                ('purchase_order_item', 'received_quantity'),
                ('purchase_order_item', 'purchase_price'),
                ('purchase_order_item', 'line_amount'),
                ('purchase_demand_allocation', 'allocated_quantity'),
                ('purchase_receipt_item', 'received_quantity'),
                ('inventory', 'quantity'),
                ('inventory', 'average_cost'),
                ('inventory_movement', 'quantity_before'),
                ('inventory_movement', 'quantity_change'),
                ('inventory_movement', 'quantity_after'),
                ('inventory_movement', 'unit_cost')
              )
            """, Integer.class);
        Integer unconstrainedWeightColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and data_type = 'numeric'
              and numeric_precision is null
              and numeric_scale is null
              and (table_name, column_name) in (
                ('purchase_receipt_item', 'actual_weight'),
                ('receipt_weighing_record', 'raw_reading'),
                ('receipt_weighing_record', 'confirmed_reading'),
                ('receipt_weighing_record', 'scale_precision')
              )
            """, Integer.class);
        Integer jsonbColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and data_type = 'jsonb'
              and (table_name, column_name) in (
                ('supplier_sku', 'spec_values_snapshot'),
                ('purchase_demand', 'spec_values_snapshot'),
                ('purchase_order_item', 'spec_values_snapshot'),
                ('purchase_demand_allocation', 'demand_snapshot'),
                ('purchase_receipt_item', 'spec_values_snapshot'),
                ('purchase_operation_log', 'before_data'),
                ('purchase_operation_log', 'after_data')
              )
            """, Integer.class);

        assertThat(numericColumns).isEqualTo(16);
        assertThat(unconstrainedWeightColumns).isEqualTo(4);
        assertThat(jsonbColumns).isEqualTo(7);
    }
}
