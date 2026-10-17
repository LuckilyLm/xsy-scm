package com.xsy.scm.product;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.product.dao.ProductBundleComponentDao;
import com.xsy.scm.product.dao.ProductSkuDao;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.manager.ProductSkuChangeSet;
import com.xsy.scm.product.manager.ProductSkuSyncManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSkuInternalCodeTest {
    private final ProductSkuDao skuDao = mock(ProductSkuDao.class);
    private final ScmBusinessNoService numbers = mock(ScmBusinessNoService.class);
    private final ProductSkuSyncManager manager = new ProductSkuSyncManager(
            skuDao, numbers, mock(ProductBundleComponentDao.class));

    @Test
    void newSkuGetsBackendCodeNotClientValue() {
        var form = new ProductSkuForm();
        form.setSkuCode("CLIENT-OVERRIDE");
        form.setSpecName("5kg/箱");
        form.setSaleUnit("箱");
        when(numbers.next(ScmBusinessNoType.PRODUCT_SKU)).thenReturn("SKU000128");

        try (var operator = org.mockito.Mockito.mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("test:1");
            manager.sync(22L, new ProductSkuChangeSet(List.of(form), List.of(), List.of()));
        }

        var result = ArgumentCaptor.forClass(ProductSkuEntity.class);
        verify(skuDao).insert(result.capture());
        assertThat(result.getValue().getSkuCode()).isEqualTo("SKU000128");
        assertThat(result.getValue().getSpuId()).isEqualTo(22L);
    }

    @Test
    void existingSkuRetainsCodeEvenIfClientAttemptsToOverwrite() {
        var existing = new ProductSkuEntity();
        existing.setId(88L);
        existing.setSpuId(22L);
        existing.setSkuCode("LEGACY-SKU");
        when(skuDao.selectById(88L)).thenReturn(existing);
        when(skuDao.updateById(org.mockito.ArgumentMatchers.any(ProductSkuEntity.class))).thenReturn(1);
        var form = new ProductSkuForm();
        form.setSkuId(88L);
        form.setVersion(0);
        form.setSkuCode("CLIENT-REWRITE");
        form.setSpecName("散称");
        form.setSaleUnit("kg");

        try (var operator = org.mockito.Mockito.mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("test:1");
            manager.sync(22L, new ProductSkuChangeSet(List.of(), List.of(form), List.of()));
        }

        var result = ArgumentCaptor.forClass(ProductSkuEntity.class);
        verify(skuDao).updateById(result.capture());
        assertThat(result.getValue().getSkuCode()).isEqualTo("LEGACY-SKU");
        verifyNoInteractions(numbers);
    }
}
