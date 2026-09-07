package com.xianshuyuan.scm.supplier.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.supplier.dto.SupplierSkuSaveRequest;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.mapper.SupplierSkuMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SupplierSkuServiceTest {
    private final SupplierService masterData = mock(SupplierService.class);
    private final SupplierSkuMapper configurations = mock(SupplierSkuMapper.class);
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final SupplierSkuService service = new SupplierSkuService(masterData, configurations, skus);

    @Test
    void locksSupplierBeforeReadingConfigurationRows() {
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(masterData, configurations);
        given(masterData.requireEnabledSupplierForUpdate(1L)).willReturn(supplier());
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of());

        service.replaceForSupplier(1L, List.of());

        order.verify(masterData).requireEnabledSupplierForUpdate(1L);
        order.verify(configurations).selectActiveBySupplierIdForUpdate(1L);
    }

    @Test
    void returnsStableViewsInsteadOfPersistenceEntities() {
        SupplierSkuEntity entity = existing(3L, 1L, 2L, 4);
        entity.setSpecValuesSnapshot(Map.of("规格", "大"));
        entity.setReferencePrice(new BigDecimal("12.3400"));
        given(configurations.selectActiveBySupplierId(1L)).willReturn(List.of(entity));

        var result = service.listForSupplier(1L);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(3L);
            assertThat(view.skuCodeSnapshot()).isEqualTo("SKU1");
            assertThat(view.specValuesSnapshot()).containsEntry("规格", "大");
            assertThat(view.version()).isEqualTo(4);
        });
    }

    @Test
    void purchasingEligibilityUsesEnabledSupplierSkuQuery() {
        SupplierSkuEntity entity = existing(3L, 1L, 2L, 4);
        given(configurations.selectEnabledBySkuId(2L)).willReturn(List.of(entity));

        assertThat(service.listEnabledForPurchasing(2L)).containsExactly(entity);
        verify(configurations).selectEnabledBySkuId(2L);
    }

    @Test
    void requiresEnabledConfigurationForSupplierAndSku() {
        SupplierSkuEntity entity = existing(3L, 1L, 2L, 4);
        given(configurations.selectEnabledBySkuId(2L)).willReturn(List.of(entity));

        assertThat(service.requireEnabledForPurchasing(1L, 2L)).isSameAs(entity);
    }

    @Test
    void rejectsMissingConfigurationForSupplierAndSku() {
        given(configurations.selectEnabledBySkuId(2L)).willReturn(List.of());

        assertThatThrownBy(() -> service.requireEnabledForPurchasing(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商 SKU");
    }

    @Test
    void mapsInvalidReferencePriceToValidationErrorOutsideMvc() {
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(ShelfStatus.ON_SHELF)));
        SupplierSkuSaveRequest request = new SupplierSkuSaveRequest(
                null, 1L, 2L, "箱", "not-a-decimal", null, false, EnabledStatus.ENABLED, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode().code())
                        .isEqualTo(40000));
    }

    @Test
    void snapshotsProductNameInsteadOfSpecificationName() {
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L)))
                .willReturn(List.of(sku(ShelfStatus.ON_SHELF)));
        given(configurations.insert(any(SupplierSkuEntity.class))).willAnswer(invocation -> {
            SupplierSkuEntity created = invocation.getArgument(0);
            created.setId(20L);
            return 1;
        });

        service.create(request(null, 1L, 2L, false, null));

        org.mockito.ArgumentCaptor<SupplierSkuEntity> captor =
                org.mockito.ArgumentCaptor.forClass(SupplierSkuEntity.class);
        verify(configurations).insert(captor.capture());
        assertThat(captor.getValue().getSkuNameSnapshot()).isEqualTo("西红柿");
    }

    @Test
    void rejectsConfigurationForDisabledSupplier() {
        given(masterData.requireEnabledSupplier(1L))
                .willThrow(new BusinessException(SupplierErrorCodes.DISABLED));

        assertThatThrownBy(() -> service.create(request(null, 1L, 2L, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
        verifyNoInteractions(skus, configurations);
    }

    @Test
    void rejectsConfigurationForOffShelfSku() {
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of());

        assertThatThrownBy(() -> service.create(request(null, 1L, 2L, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU 未启用");
        verify(configurations, never()).insert(any(SupplierSkuEntity.class));
    }

    @Test
    void rejectsDuplicateSkuBeforeChangingExistingRows() {
        var requests = List.of(
                request(null, 1L, 2L, false, null),
                request(null, 1L, 2L, false, null)
        );
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(ShelfStatus.ON_SHELF)));

        assertThatThrownBy(() -> service.replaceForSupplier(1L, requests))
                .isInstanceOf(BusinessException.class)
                .hasMessage("供应商 SKU 配置重复");
    }

    @Test
    void allowsMultipleDefaultsWithoutInventingCardinalityPolicy() {
        var requests = List.of(
                request(null, 1L, 2L, true, null),
                request(null, 1L, 4L, true, null)
        );
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of());
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(2L, ShelfStatus.ON_SHELF)));
        given(skus.selectOrderableByIds(List.of(4L))).willReturn(List.of(sku(4L, ShelfStatus.ON_SHELF)));
        given(configurations.insert(any(SupplierSkuEntity.class))).willAnswer(invocation -> {
            SupplierSkuEntity created = invocation.getArgument(0);
            created.setId(20L);
            return 1;
        });

        service.replaceForSupplier(1L, requests);

        verify(configurations, org.mockito.Mockito.times(2)).insert(any(SupplierSkuEntity.class));
    }

    @Test
    void replacementUpdatesRetainedCreatesNewAndSoftDeletesRemoved() {
        SupplierSkuEntity retained = existing(10L, 1L, 2L, 3);
        SupplierSkuEntity removed = existing(11L, 1L, 4L, 1);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(retained, removed));
        given(configurations.selectById(10L)).willReturn(retained);
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(2L, ShelfStatus.ON_SHELF)));
        given(skus.selectOrderableByIds(List.of(5L))).willReturn(List.of(sku(5L, ShelfStatus.ON_SHELF)));
        given(configurations.updateById(any(SupplierSkuEntity.class))).willReturn(1);
        given(configurations.insert(any(SupplierSkuEntity.class))).willAnswer(invocation -> {
            SupplierSkuEntity created = invocation.getArgument(0);
            created.setId(12L);
            return 1;
        });

        given(configurations.softDeleteOwnedWithVersion(1L, 11L, 1)).willReturn(1);

        service.replaceForSupplier(1L, List.of(
                request(10L, 1L, 2L, true, 3),
                request(null, 1L, 5L, false, null)
        ));

        verify(configurations).updateById(any(SupplierSkuEntity.class));
        verify(configurations).insert(any(SupplierSkuEntity.class));
        verify(configurations).softDeleteOwnedWithVersion(1L, 11L, 1);
    }

    @Test
    void validatesEveryRequestBeforeChangingExistingRows() {
        SupplierSkuEntity existing = existing(10L, 1L, 2L, 3);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(existing));
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(2L, ShelfStatus.ON_SHELF)));
        given(skus.selectOrderableByIds(List.of(5L))).willReturn(List.of());
        given(configurations.updateById(any(SupplierSkuEntity.class))).willReturn(1);

        assertThatThrownBy(() -> service.replaceForSupplier(1L, List.of(
                request(10L, 1L, 2L, false, 3),
                request(null, 1L, 5L, false, null)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU 未启用");

        verify(configurations, never()).updateById(any(SupplierSkuEntity.class));
        verify(configurations, never()).insert(any(SupplierSkuEntity.class));
        verify(configurations, never()).softDeleteOwnedWithVersion(anyLong(), anyLong(), anyInt());
    }

    @Test
    void rejectsRetainedRowOwnedByAnotherSupplier() {
        SupplierSkuEntity foreign = existing(10L, 9L, 2L, 3);
        given(configurations.selectById(10L)).willReturn(foreign);

        assertThatThrownBy(() -> service.replaceForSupplier(
                1L, List.of(request(10L, 1L, 2L, false, 3))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void reconcilesNoIdRowAgainstExistingActivePair() {
        SupplierSkuEntity existing = existing(10L, 1L, 2L, 3);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(existing));
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(ShelfStatus.ON_SHELF)));
        given(configurations.updateById(any(SupplierSkuEntity.class))).willReturn(1);

        service.replaceForSupplier(1L, List.of(request(null, 1L, 2L, false, 3)));

        verify(configurations).updateById(any(SupplierSkuEntity.class));
        verify(configurations, never()).insert(any(SupplierSkuEntity.class));
    }

    @Test
    void clearingConfigurationsDeletesEveryRowWithExpectedVersion() {
        SupplierSkuEntity existing = existing(10L, 1L, 2L, 3);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(existing));
        given(configurations.softDeleteOwnedWithVersion(1L, 10L, 3)).willReturn(1);

        service.replaceForSupplier(1L, List.of());

        verify(configurations).softDeleteOwnedWithVersion(1L, 10L, 3);
    }

    @Test
    void rejectsConcurrentChangeWhileDeletingOmittedRow() {
        SupplierSkuEntity existing = existing(10L, 1L, 2L, 3);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(existing));
        given(configurations.softDeleteOwnedWithVersion(1L, 10L, 3)).willReturn(0);

        assertThatThrownBy(() -> service.replaceForSupplier(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
    }

    @Test
    void rejectsStaleRetainedVersionBeforeUpdating() {
        SupplierSkuEntity existing = existing(10L, 1L, 2L, 3);
        given(configurations.selectActiveBySupplierIdForUpdate(1L)).willReturn(List.of(existing));

        assertThatThrownBy(() -> service.replaceForSupplier(
                1L, List.of(request(10L, 1L, 2L, false, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
        verify(configurations, never()).updateById(any(SupplierSkuEntity.class));
    }

    @Test
    void rejectsStaleVersionOnUpdate() {
        SupplierSkuEntity existing = existing(3L, 1L, 2L, 4);
        given(configurations.selectById(3L)).willReturn(existing);
        given(masterData.requireEnabledSupplier(1L)).willReturn(supplier());
        given(skus.selectOrderableByIds(List.of(2L))).willReturn(List.of(sku(ShelfStatus.ON_SHELF)));
        given(configurations.updateById(any(SupplierSkuEntity.class))).willReturn(0);

        assertThatThrownBy(() -> service.update(3L, request(3L, 1L, 2L, false, 4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
    }

    private SupplierSkuSaveRequest request(
            Long id, long supplierId, long skuId, boolean defaultSupplier, Integer version
    ) {
        return new SupplierSkuSaveRequest(id, supplierId, skuId, "箱", "12.3400",
                null, defaultSupplier, EnabledStatus.ENABLED, version);
    }

    private SupplierEntity supplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(1L);
        entity.setSupplierCode("S1");
        entity.setName("供应商");
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setDeleted(false);
        return entity;
    }

    private ProductSkuEntity sku(ShelfStatus status) {
        return sku(2L, status);
    }

    private ProductSkuEntity sku(long id, ShelfStatus status) {
        ProductSkuEntity entity = new ProductSkuEntity();
        entity.setId(id);
        entity.setSkuCode("SKU" + id);
        entity.setProductName("西红柿");
        entity.setSpecName("大");
        entity.setSpecValues(Map.of("规格", "大"));
        entity.setSaleUnit("箱");
        entity.setStatus(status);
        entity.setDeleted(false);
        return entity;
    }

    private SupplierSkuEntity existing(long id, long supplierId, long skuId, int version) {
        SupplierSkuEntity entity = new SupplierSkuEntity();
        entity.setId(id);
        entity.setSupplierId(supplierId);
        entity.setSkuId(skuId);
        entity.setSupplierCodeSnapshot("S1");
        entity.setSupplierNameSnapshot("供应商");
        entity.setSkuCodeSnapshot("SKU1");
        entity.setSkuNameSnapshot("大");
        entity.setPurchaseUnit("箱");
        entity.setDefaultSupplier(false);
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setVersion(version);
        entity.setDeleted(false);
        return entity;
    }
}
