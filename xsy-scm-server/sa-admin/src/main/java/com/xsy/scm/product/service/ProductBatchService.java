package com.xsy.scm.product.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.constant.ScmProductMasterStatusEnum;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.domain.form.ProductBatchItemForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchCategoryForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchStatusForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchTagForm;
import com.xsy.scm.product.domain.vo.ProductBatchResultVO;
import com.xsy.scm.product.domain.vo.ProductBatchResultVO.Failure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xsy.scm.product.constant.ProductErrorCode.MASTER_STATUS_SALE_CONFLICT;
import static com.xsy.scm.product.constant.ProductErrorCode.PRODUCT_NOT_FOUND;
import static com.xsy.scm.product.constant.ProductErrorCode.VERSION_CONFLICT;

/**
 * 商品批量维护：上下架、主档启停、改分类、打标签。
 * 整批一个事务，且先逐行预校验再落库：任何一行不存在或版本冲突时 updatedCount 为 0、
 * 不做任何写入，并将每个失败商品及其错误原因逐条返回。
 */
@Service
@RequiredArgsConstructor
public class ProductBatchService {
    /**
     * 失败行只回前 50 条，避免一次批量误操作把响应撑爆；failedCount 仍是全量。
     */
    private static final int MAX_FAILURES_SHOWN = 50;
    private final ProductSpuDao productSpuDao;
    private final ProductCategoryService productCategoryService;
    private final ProductTagService productTagService;

    @Transactional
    public ProductBatchResultVO updateStatus(ProductSpuBatchStatusForm form) {
        var locked = lock(form.getItems());
        List<Failure> failures = new ArrayList<>();
        for (var item : form.getItems()) {
            var entity = locked.get(item.getSpuId());
            var failure = check(item, entity);
            if (failure != null) {
                failures.add(failure);
                continue;
            }
            // 归档即退出经营：批量入口同样不能造出「已归档还在架」的组合。
            var status = form.getStatus() != null ? form.getStatus() : entity.getStatus();
            if (ScmProductMasterStatusEnum.ARCHIVED.name().equals(form.getMasterStatus())
                    && ScmShelfStatusEnum.ON_SHELF.name().equals(status))
                failures.add(failure(entity, MASTER_STATUS_SALE_CONFLICT));
        }
        return commit(failures, form.getItems(), (ids) -> productSpuDao.batchApply(ids, form.getStatus(), form.getMasterStatus(), null, ScmOperator.current()));
    }

    @Transactional
    public ProductBatchResultVO updateCategory(ProductSpuBatchCategoryForm form) {
        productCategoryService.requireSelectableCategory(form.getCategoryId());
        var failures = verify(form.getItems());
        return commit(failures, form.getItems(), (ids) -> productSpuDao.batchApply(ids, null, null, form.getCategoryId(), ScmOperator.current()));
    }

    /**
     * REPLACE 用 tagIds 覆盖现有标签（空集合即清空）；ADD / REMOVE 只处理列出的标签。
     */
    @Transactional
    public ProductBatchResultVO updateTags(ProductSpuBatchTagForm form) {
        var failures = verify(form.getItems());
        // 批量打标的语义就是「新引用」，所以本次给出的标签必须全部可用；
        // REMOVE 不校验，否则停用标签再也摘不掉。放在 verify 之后以沿用「先商品后标签」的锁序。
        if (!"REMOVE".equals(form.getMode())) productTagService.assertUsable(form.getTagIds());
        return commit(failures, form.getItems(), (ids) -> {
            if ("ADD".equals(form.getMode())) productTagService.addTags(ids, form.getTagIds());
            else if ("REMOVE".equals(form.getMode())) productTagService.removeTags(ids, form.getTagIds());
            else productTagService.replaceTags(ids, form.getTagIds());
        });
    }

    private List<Failure> verify(List<ProductBatchItemForm> items) {
        var locked = lock(items);
        return items.stream().map(item -> check(item, locked.get(item.getSpuId()))).filter(Objects::nonNull).toList();
    }

    /**
     * 按 id 升序加行锁，与单条编辑和并发批量入口互斥；重复 spuId 只锁一次。
     */
    private Map<Long, ProductSpuEntity> lock(List<ProductBatchItemForm> items) {
        return productSpuDao
                .lockByIds(items.stream().map(ProductBatchItemForm::getSpuId).distinct().sorted().toList()).stream()
                .collect(Collectors.toMap(ProductSpuEntity::getId, Function.identity()));
    }

    private Failure check(ProductBatchItemForm item, ProductSpuEntity entity) {
        if (entity == null)
            return new Failure(item.getSpuId(), null, PRODUCT_NOT_FOUND.getCode(), PRODUCT_NOT_FOUND.getMsg());
        return Objects.equals(entity.getVersion(), item.getVersion()) ? null : failure(entity, VERSION_CONFLICT);
    }

    private Failure failure(ProductSpuEntity entity, ScmErrorCode code) {
        return new Failure(entity.getId(), entity.getSpuCode(), code.getCode(), code.getMsg());
    }

    /**
     * 预校验有失败行时只回结果、不落库；全部通过才执行这一次批量写入。
     */
    private ProductBatchResultVO commit(List<Failure> failures, List<ProductBatchItemForm> items, java.util.function.Consumer<List<Long>> write) {
        var result = new ProductBatchResultVO();
        if (!failures.isEmpty()) {
            result.setFailedCount(failures.size());
            result.setFailures(failures.stream().limit(MAX_FAILURES_SHOWN).toList());
            return result;
        }
        var ids = items.stream().map(ProductBatchItemForm::getSpuId).distinct().toList();
        write.accept(ids);
        result.setUpdatedCount(ids.size());
        return result;
    }
}
