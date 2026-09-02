package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductCategorySaveRequest;
import com.xianshuyuan.scm.product.entity.ProductCategoryEntity;
import com.xianshuyuan.scm.product.mapper.ProductCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductCategoryServiceTest {

    private ProductCategoryMapper mapper;
    private ProductCategoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductCategoryMapper.class);
        service = new ProductCategoryService(mapper);
    }

    @Test
    void derivesChildLevelFromParent() {
        given(mapper.selectById(10L)).willReturn(category(10L, null, 1, 10));
        given(mapper.insert(any(ProductCategoryEntity.class))).willAnswer(invocation -> {
            ProductCategoryEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        });

        long id = service.create(new ProductCategorySaveRequest(
            10L, "vegetables", "蔬菜", 20, "ENABLED"
        ));

        ArgumentCaptor<ProductCategoryEntity> captor = ArgumentCaptor.forClass(ProductCategoryEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(id).isEqualTo(11L);
        assertThat(captor.getValue().getLevel()).isEqualTo(2);
        assertThat(captor.getValue().getCategoryCode()).isEqualTo("VEGETABLES");
    }

    @Test
    void rejectsFourthLevelCategory() {
        given(mapper.selectById(30L)).willReturn(category(30L, 20L, 3, 10));

        assertThatThrownBy(() -> service.create(new ProductCategorySaveRequest(
            30L, "too-deep", "第四级", 10, "ENABLED"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("三级");
    }

    @Test
    void returnsSortedCategoryTree() {
        given(mapper.selectActiveCategories()).willReturn(List.of(
            category(3L, 1L, 2, 20),
            category(2L, 1L, 2, 10),
            category(1L, null, 1, 10)
        ));

        var tree = service.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().children())
            .extracting(node -> node.id())
            .containsExactly(2L, 3L);
    }

    @Test
    void refusesToDeleteCategoryWithActiveProducts() {
        given(mapper.selectById(30L)).willReturn(category(30L, 20L, 3, 10));
        given(mapper.countActiveChildren(30L)).willReturn(0L);
        given(mapper.countActiveProducts(30L)).willReturn(1L);

        assertThatThrownBy(() -> service.delete(30L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("商品");
    }

    private static ProductCategoryEntity category(
        long id,
        Long parentId,
        int level,
        int sortOrder
    ) {
        ProductCategoryEntity entity = new ProductCategoryEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setCategoryCode("CAT-" + id);
        entity.setName("分类" + id);
        entity.setLevel(level);
        entity.setSortOrder(sortOrder);
        entity.setStatus("ENABLED");
        entity.setVersion(0);
        entity.setDeleted(false);
        return entity;
    }
}
