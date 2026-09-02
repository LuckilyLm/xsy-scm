package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSkuChangeSetTest {

    @Test
    void retainsExistingIdsAndSeparatesInsertAndRemoval() {
        ProductSkuChangeSet result = ProductSkuChangeSet.between(
            List.of(existing(11L), existing(12L)),
            List.of(request(11L, "SKU-KEEP"), request(null, "SKU-NEW"))
        );

        assertThat(result.updated()).extracting(ProductSkuSaveRequest::id).containsExactly(11L);
        assertThat(result.inserted()).extracting(ProductSkuSaveRequest::skuCode).containsExactly("SKU-NEW");
        assertThat(result.removedIds()).containsExactly(12L);
    }

    @Test
    void rejectsSkuIdOwnedByAnotherSpu() {
        assertThatThrownBy(() -> ProductSkuChangeSet.between(
            List.of(existing(11L)),
            List.of(request(99L, "SKU-FOREIGN"))
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前商品");
    }

    private static ProductSkuEntity existing(long id) {
        ProductSkuEntity entity = new ProductSkuEntity();
        entity.setId(id);
        return entity;
    }

    private static ProductSkuSaveRequest request(Long id, String code) {
        return new ProductSkuSaveRequest(
            id, id == null ? null : 0, code, null, code, Map.of(), "斤",
            ProductType.NON_STANDARD, BigDecimal.ONE, ShelfStatus.ON_SHELF,
            "SKU-KEEP".equals(code), 10
        );
    }
}
