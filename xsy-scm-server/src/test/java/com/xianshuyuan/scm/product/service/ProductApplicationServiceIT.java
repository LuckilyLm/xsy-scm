package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductApplicationServiceIT {

    @Autowired
    private ProductApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updatesSkuCollectionWithoutChangingRetainedSkuId() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long categoryId = jdbcTemplate.queryForObject("""
            select id from product_category
            where category_code = 'FRESH-FRUIT' and deleted = false
            """, Long.class);
        long spuId = service.create(product(
            null, "SPU-" + suffix, categoryId,
            List.of(
                sku(null, null, "SKU-KEEP-" + suffix, true, "散装"),
                sku(null, null, "SKU-REMOVE-" + suffix, false, "礼盒")
            )
        ));

        List<Map<String, Object>> before = jdbcTemplate.queryForList("""
            select id, sku_code, version from product_sku
            where spu_id = ? and deleted = false order by id
            """, spuId);
        long retainedId = ((Number) before.getFirst().get("id")).longValue();
        int retainedVersion = ((Number) before.getFirst().get("version")).intValue();
        long removedId = ((Number) before.get(1).get("id")).longValue();
        int spuVersion = jdbcTemplate.queryForObject(
            "select version from product_spu where id = ?", Integer.class, spuId
        );

        service.update(spuId, product(
            spuVersion, "SPU-" + suffix, categoryId,
            List.of(
                sku(retainedId, retainedVersion, "SKU-KEEP-" + suffix, true, "精品散装"),
                sku(null, null, "SKU-NEW-" + suffix, false, "10斤礼盒")
            )
        ));

        Long retainedIdAfter = jdbcTemplate.queryForObject(
            "select id from product_sku where sku_code = ? and deleted = false",
            Long.class, "SKU-KEEP-" + suffix
        );
        Boolean removed = jdbcTemplate.queryForObject(
            "select deleted from product_sku where id = ?", Boolean.class, removedId
        );
        Long insertedId = jdbcTemplate.queryForObject(
            "select id from product_sku where sku_code = ? and deleted = false",
            Long.class, "SKU-NEW-" + suffix
        );

        assertThat(retainedIdAfter).isEqualTo(retainedId);
        assertThat(removed).isTrue();
        assertThat(insertedId).isNotEqualTo(retainedId).isNotEqualTo(removedId);
    }

    private static ProductSaveRequest product(
        Integer version,
        String spuCode,
        Long categoryId,
        List<ProductSkuSaveRequest> skus
    ) {
        return new ProductSaveRequest(
            version, spuCode, "测试苹果", "苹果", categoryId,
            "事务集成测试", ShelfStatus.ON_SHELF, skus
        );
    }

    private static ProductSkuSaveRequest sku(
        Long id,
        Integer version,
        String skuCode,
        boolean defaultSku,
        String specName
    ) {
        return new ProductSkuSaveRequest(
            id, version, skuCode, null, specName, Map.of("规格", specName), "斤",
            ProductType.NON_STANDARD, new BigDecimal("9.9900"), ShelfStatus.ON_SHELF,
            defaultSku, defaultSku ? 10 : 20
        );
    }
}
