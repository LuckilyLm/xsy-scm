package com.xsy.scm.mall;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.mall.catalog.dao.MallCatalogDao;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.product.domain.form.ProductCategoryAddForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.service.ProductCategoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 分类导航与商品可见性分离，空目录不得因商品数量被隐藏。 */
class MallCategoryNavigationPgIT extends ScmW5PgITBase {
    @Autowired
    private ProductCategoryService categories;
    @Autowired
    private MallCatalogService catalog;
    @Autowired
    private MallCatalogDao catalogDao;

    @Test
    void enabledEmptyRootAndDescendantsRemainVisible() {
        Long root = addCategory("空水产", null);
        Long child = addCategory("空鱼类", root);
        Long leaf = addCategory("空鱼规格", child);

        MallCategoryVO visible = catalog.categories().stream()
            .filter(item -> item.getCategoryId().equals(root)).findFirst().orElseThrow();
        assertThat(visible.getChildren()).extracting(MallCategoryVO::getCategoryId).containsExactly(child);
        assertThat(visible.getChildren().getFirst().getChildren())
            .extracting(MallCategoryVO::getCategoryId).containsExactly(leaf);
        var query = new MallProductQueryForm();
        query.setCategoryId(root);
        assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), newCustomer(), query)).isEmpty();
    }

    @Test
    void disabledAndDeletedAncestorsDoNotPromoteChildrenToRoots() {
        Long disabled = addCategory("停用根", null);
        Long disabledChild = addCategory("停用根的子类", disabled);
        Long deleted = addCategory("删除根", null);
        Long deletedChild = addCategory("删除根的子类", deleted);
        // 模拟存量父级状态异常，读取必须整棵关闭，不把子类当成新的一级入口。
        jdbc.update("UPDATE xsy_product_category SET status='DISABLED' WHERE id=?", disabled);
        jdbc.update("UPDATE xsy_product_category SET deleted=TRUE WHERE id=?", deleted);
        evictMybatisCache();

        assertThat(catalogDao.categories()).extracting(MallCategoryVO::getCategoryId)
            .doesNotContain(disabled, disabledChild, deleted, deletedChild);
    }

    @Test
    void showingEmptyNavigationDoesNotBroadenProductVisibility() {
        Long customerId = newCustomer();
        Long root = addCategory("可见性根", null);
        Long child = addCategory("可见性子类", root);
        Long leaf = addCategory("可见性叶子", child);
        Long visibleSku = addProduct(leaf, "可见导航商品", "ON_SHELF");
        Long offShelfSku = addProduct(leaf, "下架导航商品", "OFF_SHELF");
        var query = new MallProductQueryForm();
        query.setKeyword(prefix);

        assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), customerId, query))
            .extracting(MallProductVO::getSkuId).contains(visibleSku).doesNotContain(offShelfSku);
        jdbc.update("UPDATE xsy_customer SET visibility_policy='ALLOWLIST' WHERE id=?", customerId);
        evictMybatisCache();
        assertThat(catalogDao.categories()).isNotEmpty();
        assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), customerId, query)).isEmpty();
    }

    private Long addProduct(Long categoryId, String suffix, String status) {
        // 使用独立的三级分类，不能依赖旧种子FRESH-FRUIT在当前库里的层级。
        var form = new ProductSpuAddForm();
        form.setName(prefix + suffix);
        form.setCategoryId(categoryId);
        form.setStatus(status);
        var sku = new ProductSkuForm();
        sku.setSpecName(suffix);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(true);
        form.setSkuList(new ArrayList<>(List.of(sku)));
        Long spuId = productSpuService.add(form);
        return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE", Long.class, spuId);
    }

    private Long addCategory(String suffix, Long parentId) {
        var form = new ProductCategoryAddForm();
        form.setName(prefix + suffix);
        form.setParentId(parentId);
        form.setSortOrder(0);
        form.setStatus("ENABLED");
        return categories.add(form);
    }
}
