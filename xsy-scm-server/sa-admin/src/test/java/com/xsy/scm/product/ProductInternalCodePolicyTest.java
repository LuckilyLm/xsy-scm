package com.xsy.scm.product;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.product.dao.ProductCategoryDao;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.dao.ProductTagDao;
import com.xsy.scm.product.dao.ProductTagRelationDao;
import com.xsy.scm.product.domain.entity.ProductCategoryEntity;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.domain.entity.ProductTagEntity;
import com.xsy.scm.product.domain.form.ProductCategoryAddForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductTagAddForm;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import com.xsy.scm.product.manager.ProductImageSyncManager;
import com.xsy.scm.product.manager.ProductSkuSyncManager;
import com.xsy.scm.product.service.ProductCategoryService;
import com.xsy.scm.product.service.ProductSpuService;
import com.xsy.scm.product.service.ProductTagService;
import com.xsy.scm.product.service.ProductUomService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductInternalCodePolicyTest {
    private final ScmBusinessNoService numbers = mock(ScmBusinessNoService.class);

    @Test
    void createsSpuUsingSystemCodeDespiteClientCode() {
        var spuDao = mock(ProductSpuDao.class);
        var service = new ProductSpuService(spuDao, numbers, mock(ProductCategoryService.class),
                mock(ProductAggregateValidator.class), mock(ProductSkuSyncManager.class),
                mock(ProductImageSyncManager.class), mock(ProductUomService.class),
                mock(ProductTagService.class));
        var form = new ProductSpuAddForm();
        form.setSpuCode("CLIENT-SPU");
        form.setName("红富士苹果");
        form.setCategoryId(10L);
        form.setSkuList(List.of(new ProductSkuForm()));
        form.setImages(List.of());
        form.setTagIds(List.of());
        when(numbers.next(ScmBusinessNoType.PRODUCT_SPU)).thenReturn("SPU000008");
        org.mockito.Mockito.doAnswer(invocation -> {
            ((ProductSpuEntity) invocation.getArgument(0)).setId(66L);
            return 1;
        }).when(spuDao).insert(org.mockito.ArgumentMatchers.any(ProductSpuEntity.class));

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("test:1");
            service.add(form);
        }

        var captured = ArgumentCaptor.forClass(ProductSpuEntity.class);
        verify(spuDao).insert(captured.capture());
        assertThat(captured.getValue().getSpuCode()).isEqualTo("SPU000008");
    }

    @Test
    void createsCategoryUsingSystemCodeDespiteClientCode() {
        var dao = mock(ProductCategoryDao.class);
        var service = new ProductCategoryService(dao, numbers, mock(ProductSpuDao.class));
        var form = new ProductCategoryAddForm();
        form.setCategoryCode("CLIENT-CATEGORY");
        form.setName("生鲜");
        when(numbers.next(ScmBusinessNoType.PRODUCT_CATEGORY)).thenReturn("CAT000008");

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("test:1");
            service.add(form);
        }
        var captured = ArgumentCaptor.forClass(ProductCategoryEntity.class);
        verify(dao).insert(captured.capture());
        assertThat(captured.getValue().getCategoryCode()).isEqualTo("CAT000008");
    }

    @Test
    void createsTagUsingSystemCodeDespiteClientCode() {
        var dao = mock(ProductTagDao.class);
        var service = new ProductTagService(dao, numbers, mock(ProductTagRelationDao.class));
        var form = new ProductTagAddForm();
        form.setTagCode("CLIENT-TAG");
        form.setName("精选");
        when(numbers.next(ScmBusinessNoType.PRODUCT_TAG)).thenReturn("TAG000008");

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("test:1");
            service.add(form);
        }

        var captured = ArgumentCaptor.forClass(ProductTagEntity.class);
        verify(dao).insert(captured.capture());
        assertThat(captured.getValue().getTagCode()).isEqualTo("TAG000008");
    }
}
