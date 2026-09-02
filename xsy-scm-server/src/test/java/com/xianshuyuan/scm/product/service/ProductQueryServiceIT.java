package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.product.dto.ProductPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductQueryServiceIT {

    @Autowired
    private ProductQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsSpuBySkuCodeAndBuildsSummaryWithoutNPlusOneShape() {
        var page = service.page(new ProductPageQuery(
            1, 20, "SKU-APPLE-BOX5", null, null, null, null
        ));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(product -> {
            assertThat(product.spuCode()).isEqualTo("SPU-APPLE-001");
            assertThat(product.categoryPath()).isEqualTo("生鲜食材/新鲜蔬果/新鲜水果");
            assertThat(product.defaultSku().skuCode()).isEqualTo("SKU-APPLE-JIN");
            assertThat(product.skuCount()).isEqualTo(2);
            assertThat(product.minMarketPrice()).isEqualTo("6.9800");
            assertThat(product.maxMarketPrice()).isEqualTo("39.9000");
        });
    }

    @Test
    void readsJsonbSpecificationSnapshotInProductDetail() {
        Long spuId = jdbcTemplate.queryForObject(
            "select id from product_spu where spu_code = 'SPU-APPLE-001' and deleted = false",
            Long.class
        );

        var detail = service.get(spuId);

        assertThat(detail.skus()).extracting(sku -> sku.specValues().get("包装"))
            .containsExactly(null, "礼盒");
    }
}
