package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderItemChangeSetTest {

    @Test
    void separatesRetainedInsertedAndRemovedItemsWithoutChangingIdentity() {
        SalesOrderItemEntity retained = item(11L, 7L, 2);
        SalesOrderItemEntity removed = item(12L, 7L, 0);
        SalesOrderItemEntity requestedRetained = item(11L, 7L, 2);
        SalesOrderItemEntity requestedNew = item(null, null, null);

        SalesOrderItemChangeSet result = SalesOrderItemChangeSet.between(
            7L, List.of(retained, removed), List.of(requestedRetained, requestedNew)
        );

        assertThat(result.updated()).containsExactly(requestedRetained);
        assertThat(result.inserted()).containsExactly(requestedNew);
        assertThat(result.removedIds()).containsExactly(12L);
    }

    @Test
    void rejectsRetainedItemWithoutVersion() {
        assertThatThrownBy(() -> SalesOrderItemChangeSet.between(
            7L, List.of(item(11L, 7L, 2)), List.of(item(11L, 7L, null))
        )).isInstanceOf(BusinessException.class).hasMessageContaining("版本");
    }

    @Test
    void rejectsItemIdNotOwnedByOrder() {
        assertThatThrownBy(() -> SalesOrderItemChangeSet.between(
            7L, List.of(item(11L, 7L, 2)), List.of(item(99L, 7L, 0))
        )).isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前订单");
    }

    @Test
    void rejectsStaleItemVersion() {
        assertThatThrownBy(() -> SalesOrderItemChangeSet.between(
            7L, List.of(item(11L, 7L, 2)), List.of(item(11L, 7L, 1))
        )).isInstanceOf(BusinessException.class).hasMessageContaining("版本冲突");
    }

    private static SalesOrderItemEntity item(Long id, Long orderId, Integer version) {
        SalesOrderItemEntity item = new SalesOrderItemEntity();
        item.setId(id);
        item.setOrderId(orderId);
        item.setVersion(version);
        return item;
    }
}
