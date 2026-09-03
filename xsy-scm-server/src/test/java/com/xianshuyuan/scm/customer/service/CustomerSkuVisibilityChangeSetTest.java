package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerSkuVisibilityRequest;
import com.xianshuyuan.scm.customer.entity.CustomerSkuVisibilityEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerSkuVisibilityChangeSetTest {
    @Test
    void preservesRetainedIdentityAndSeparatesChanges() {
        var changes = CustomerSkuVisibilityChangeSet.between(
            List.of(existing(1, 10), existing(2, 20)),
            List.of(new CustomerSkuVisibilityRequest(1L, 0, 10L), new CustomerSkuVisibilityRequest(null, null, 30L))
        );
        assertThat(changes.updated()).extracting(CustomerSkuVisibilityRequest::id).containsExactly(1L);
        assertThat(changes.inserted()).extracting(CustomerSkuVisibilityRequest::skuId).containsExactly(30L);
        assertThat(changes.removedIds()).containsExactly(2L);
    }

    @Test
    void rejectsForeignVisibilityIdentity() {
        assertThatThrownBy(() -> CustomerSkuVisibilityChangeSet.between(
            List.of(existing(1, 10)), List.of(new CustomerSkuVisibilityRequest(99L, 0, 10L))))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前客户");
    }

    @Test
    void rejectsRetainedVisibilitySkuReplacement() {
        assertThatThrownBy(() -> CustomerSkuVisibilityChangeSet.between(
            List.of(existing(1, 10)), List.of(new CustomerSkuVisibilityRequest(1L, 0, 20L))))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不能更换 SKU");
    }

    @Test
    void rejectsDuplicateRequestedSku() {
        assertThatThrownBy(() -> CustomerSkuVisibilityChangeSet.between(List.of(), List.of(
            new CustomerSkuVisibilityRequest(null, null, 10L),
            new CustomerSkuVisibilityRequest(null, null, 10L))))
            .isInstanceOf(BusinessException.class).hasMessageContaining("重复");
    }

    private static CustomerSkuVisibilityEntity existing(long id, long skuId) {
        var entity = new CustomerSkuVisibilityEntity(); entity.setId(id); entity.setSkuId(skuId); return entity;
    }
}
