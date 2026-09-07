package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductCategorySaveRequest;
import com.xianshuyuan.scm.product.entity.ProductCategoryEntity;
import com.xianshuyuan.scm.product.mapper.ProductCategoryMapper;
import com.xianshuyuan.scm.product.vo.ProductCategoryTreeNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductCategoryService {

    private static final int MAX_LEVEL = 3;
    private final ProductCategoryMapper mapper;

    public ProductCategoryService(ProductCategoryMapper mapper) {
        this.mapper = mapper;
    }

    public List<ProductCategoryTreeNode> getTree() {
        List<ProductCategoryEntity> categories = mapper.selectActiveCategories();
        Map<Long, List<ProductCategoryEntity>> childrenByParent = new HashMap<>();
        List<ProductCategoryEntity> roots = new ArrayList<>();
        for (ProductCategoryEntity category : categories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }
        Comparator<ProductCategoryEntity> order = Comparator
                .comparing(ProductCategoryEntity::getSortOrder)
                .thenComparing(ProductCategoryEntity::getId);
        roots.sort(order);
        childrenByParent.values().forEach(children -> children.sort(order));
        return roots.stream().map(root -> toNode(root, childrenByParent)).toList();
    }

    @Transactional
    public long create(ProductCategorySaveRequest request) {
        ProductCategoryEntity category = new ProductCategoryEntity();
        applyRequest(category, request, null);
        mapper.insert(category);
        return category.getId();
    }

    @Transactional
    public void update(long id, ProductCategorySaveRequest request) {
        ProductCategoryEntity category = requireCategory(id);
        applyRequest(category, request, id);
        mapper.updateById(category);
    }

    @Transactional
    public void delete(long id) {
        requireCategory(id);
        if (mapper.countActiveChildren(id) > 0) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_HAS_CHILDREN);
        }
        if (mapper.countActiveProducts(id) > 0) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_HAS_PRODUCTS);
        }
        mapper.deleteById(id);
    }

    public ProductCategoryEntity requireSelectableCategory(long id) {
        ProductCategoryEntity category = requireCategory(id);
        if (category.getLevel() != MAX_LEVEL || !"ENABLED".equals(category.getStatus())) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_PARENT_INVALID,
                    "商品必须选择已启用的第三级分类");
        }
        return category;
    }

    private void applyRequest(
            ProductCategoryEntity category,
            ProductCategorySaveRequest request,
            Long currentId
    ) {
        int level = resolveLevel(request.parentId(), currentId);
        category.setParentId(request.parentId());
        category.setCategoryCode(request.categoryCode().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setLevel(level);
        category.setSortOrder(request.sortOrder());
        category.setStatus(request.status());
        category.setUpdatedBy("SYSTEM");
        if (category.getId() == null) {
            category.setVersion(0);
            category.setDeleted(false);
            category.setCreatedBy("SYSTEM");
        }
    }

    private int resolveLevel(Long parentId, Long currentId) {
        if (parentId == null) {
            return 1;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_PARENT_INVALID);
        }
        ProductCategoryEntity parent = requireCategory(parentId);
        int level = parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_LEVEL_INVALID);
        }
        if (!"ENABLED".equals(parent.getStatus())) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_PARENT_INVALID,
                    "不能在已停用分类下新增子分类");
        }
        return level;
    }

    private ProductCategoryEntity requireCategory(long id) {
        ProductCategoryEntity category = mapper.selectById(id);
        if (category == null || Boolean.TRUE.equals(category.getDeleted())) {
            throw new BusinessException(ProductErrorCodes.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private ProductCategoryTreeNode toNode(
            ProductCategoryEntity category,
            Map<Long, List<ProductCategoryEntity>> childrenByParent
    ) {
        List<ProductCategoryTreeNode> children = childrenByParent
                .getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> toNode(child, childrenByParent))
                .toList();
        return new ProductCategoryTreeNode(
                category.getId(), category.getParentId(), category.getCategoryCode(),
                category.getName(), category.getLevel(), category.getSortOrder(),
                category.getStatus(), children
        );
    }
}
