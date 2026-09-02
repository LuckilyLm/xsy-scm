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
}
