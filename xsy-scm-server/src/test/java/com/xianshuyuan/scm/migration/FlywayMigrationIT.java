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

    @Test
    void createsSprintFourRbacSessionLogAndDictionarySchemaWithoutForeignKeys() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'sys_department', 'sys_role', 'sys_permission', 'sys_menu',
                'sys_user_role', 'sys_role_permission', 'sys_role_menu',
                'spring_session', 'spring_session_attributes',
                'sys_login_log', 'sys_operation_log',
                'sys_dictionary', 'sys_dictionary_item'
              )
            """, Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = 'public'
              and constraint_type = 'FOREIGN KEY'
            """, Integer.class);

        assertThat(tables).isEqualTo(13);
        assertThat(foreignKeys).isZero();
    }

    @Test
    void extendsExistingUsersAndCreatesSessionPrincipalIndex() {
        Integer userColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and table_name = 'sys_user'
              and column_name in (
                'password_hash', 'password_changed_at', 'department_id',
                'email', 'phone', 'administrator', 'must_change_password',
                'auth_version', 'failed_login_count', 'locked_until', 'last_login_at'
              )
            """, Integer.class);
        Integer sessionIndexes = jdbcTemplate.queryForObject("""
            select count(*) from pg_indexes
            where schemaname = 'public'
              and indexname in ('spring_session_ix1', 'spring_session_ix2', 'spring_session_ix3')
            """, Integer.class);

        assertThat(userColumns).isEqualTo(11);
        assertThat(sessionIndexes).isEqualTo(3);
    }

    @Test
    void createsSprintFourActiveUniquenessIndexes() {
        Integer indexes = jdbcTemplate.queryForObject("""
            select count(*) from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'uk_sys_department_code_active', 'uk_sys_user_email_active',
                'uk_sys_user_phone_active', 'uk_sys_role_code_active',
                'uk_sys_permission_code_active', 'uk_sys_menu_route_key_active',
                'uk_sys_user_role_active', 'uk_sys_role_permission_active',
                'uk_sys_role_menu_active', 'uk_sys_dictionary_code_active',
                'uk_sys_dictionary_item_value_active'
              )
              and indexdef ilike '% where %'
            """, Integer.class);

        assertThat(indexes).isEqualTo(11);
    }

    @Test
    void usesJsonbForSystemOperationSnapshots() {
        Integer jsonbColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and table_name = 'sys_operation_log'
              and column_name in ('before_data', 'after_data')
              and data_type = 'jsonb'
            """, Integer.class);

        assertThat(jsonbColumns).isEqualTo(2);
    }
    @Test
    void createsReceiptConfirmationTablesAndSequenceWithoutForeignKeys() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'purchase_receipt_confirmation',
                'purchase_receipt_confirmation_item'
              )
            """, Integer.class);
        Integer sequences = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.sequences
            where sequence_schema = 'public'
              and sequence_name = 'purchase_receipt_confirmation_no_seq'
            """, Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = 'public'
              and table_name in (
                'purchase_receipt_confirmation',
                'purchase_receipt_confirmation_item'
              )
              and constraint_type = 'FOREIGN KEY'
            """, Integer.class);

        assertThat(tables).isEqualTo(2);
        assertThat(sequences).isOne();
        assertThat(foreignKeys).isZero();
    }

    @Test
    void createsReceiptConfirmationConstraintsAndIndexes() {
        assertThat(constraintDefinition("ck_purchase_receipt_status").toLowerCase())
            .contains("draft", "partially_confirmed", "confirmed");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation").toLowerCase())
            .contains("status", "confirmed_at");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_total").toLowerCase())
            .contains("total_quantity", ">");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_status").toLowerCase())
            .contains("status", "confirmed");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_result").toLowerCase())
            .contains("result_data", "jsonb_typeof", "object");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_item_planned").toLowerCase())
            .contains("planned_quantity", ">", "0");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_item_effective").toLowerCase())
            .contains("effective_quantity", ">", "0");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_item_weight").toLowerCase())
            .contains("actual_weight", ">", "0");
        assertThat(constraintDefinition("ck_purchase_receipt_confirmation_item_source").toLowerCase())
            .contains("weighing_source", "manual");

        assertThat(indexDefinition("uk_purchase_receipt_confirmation_key").toLowerCase())
            .contains("unique", "(idempotency_scope, idempotency_key)");
        assertThat(indexDefinition("uk_purchase_receipt_confirmation_no").toLowerCase())
            .contains("unique", "(confirmation_no)");
        assertThat(indexDefinition("idx_purchase_receipt_confirmation_receipt").toLowerCase())
            .contains("(purchase_receipt_id, confirmed_at desc)");
        assertThat(indexDefinition("uk_purchase_receipt_confirmation_item").toLowerCase())
            .contains("unique", "(confirmation_id, purchase_receipt_item_id)");
        assertThat(indexDefinition("idx_purchase_receipt_confirmation_item_receipt_item").toLowerCase())
            .contains("(purchase_receipt_item_id, confirmation_id)");
    }

    @Test
    void hardensReceiptConfirmationIdempotencyAndReceivedQuantity() {
        Integer idempotencyKeyWidth = jdbcTemplate.queryForObject("""
            select character_maximum_length
            from information_schema.columns
            where table_schema = 'public'
              and table_name = 'purchase_receipt_confirmation'
              and column_name = 'idempotency_key'
            """, Integer.class);

        assertThat(idempotencyKeyWidth).isEqualTo(200);
        assertThat(constraintDefinition("ck_purchase_order_item_received_not_over_planned").toLowerCase())
            .contains("received_quantity", "<=", "planned_quantity");
    }

    @Test
    void requiresConfirmationForPurchaseInMovementsAndIndexesIt() {
        assertThat(constraintDefinition("ck_inventory_movement_confirmation").toLowerCase())
            .contains("movement_type", "purchase_in", "confirmation_id", "is not null");
        assertThat(indexDefinition("uk_inventory_movement_receipt_confirmation").toLowerCase())
            .contains("unique",
                "(source_document_type, source_document_id, source_document_item_id, confirmation_id)",
                "deleted = false", "movement_type", "purchase_in");
        assertThat(indexDefinition("idx_inventory_movement_confirmation").toLowerCase())
            .contains("(confirmation_id)", "confirmation_id is not null");
    }

    @Test
    void makesInventoryMovementsAppendOnlyWithDatabaseTrigger() {
        String triggerDefinition = jdbcTemplate.queryForObject("""
            select pg_get_triggerdef(t.oid)
            from pg_trigger t
            join pg_class c on c.oid = t.tgrelid
            join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public'
              and c.relname = 'inventory_movement'
              and t.tgname = 'trg_inventory_movement_append_only'
              and not t.tgisinternal
            """, String.class);
        String functionDefinition = jdbcTemplate.queryForObject("""
            select pg_get_functiondef(p.oid)
            from pg_proc p
            join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'public'
              and p.proname = 'reject_inventory_movement_mutation'
            """, String.class);

        assertThat(triggerDefinition.toLowerCase())
            .contains("before", "update", "delete", "on public.inventory_movement", "for each row",
                "reject_inventory_movement_mutation");
        assertThat(functionDefinition.toLowerCase())
            .contains("returns trigger", "inventory movements are append-only");
    }

    @Test
    void makesReceiptConfirmationSequenceUsable() {
        Long confirmationNumber = jdbcTemplate.queryForObject(
            "select nextval('purchase_receipt_confirmation_no_seq')", Long.class);

        assertThat(confirmationNumber).isPositive();
    }

    @Test
    void createsP1PurchaseGenerationAndConfigSchema() {
        Integer tables = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema = 'public' and table_name in ('sys_config', 'purchase_demand_generation_batch')
            """, Integer.class);
        assertThat(tables).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select config_value from sys_config where config_key = 'purchase.over_receipt_tolerance_percent'", String.class))
            .isEqualTo("10");
        assertThat(indexDefinition("uk_purchase_demand_batch_idempotency").toLowerCase())
            .contains("unique", "idempotency_scope", "idempotency_key");
    }

    @Test
    void evolvesP1ReceiptAndPurchaseStatusModel() {
        assertThat(constraintDefinition("ck_purchase_order_status").toLowerCase()).contains("short_closed");
        Integer receiptColumns = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.columns where table_schema = 'public'
              and table_name = 'purchase_receipt'
              and column_name in ('receipt_mode', 'putaway_status', 'putaway_at', 'reason')
            """, Integer.class);
        assertThat(receiptColumns).isEqualTo(4);
        assertThat(indexDefinition("idx_inventory_movement_receipt_id").toLowerCase()).contains("receipt_id");
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
