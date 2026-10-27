package com.xsy.scm.businesscategory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.businesscategory.constant.BusinessCategoryErrorCode;
import com.xsy.scm.businesscategory.constant.BusinessCategoryScopeEnum;
import com.xsy.scm.businesscategory.dao.BusinessCategoryDao;
import com.xsy.scm.businesscategory.dao.BusinessCategoryReferenceDao;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryDeleteForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryTreeVO;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryVO;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

/**
 * 客户 / 供应商业务分类读写。两类主体共用一张表，但树、编码和名称都按 scope 隔离。
 *
 * <p>
 * 分类最多三级；父级必须同作用域且启用。移动分类时如果层级变化， 不允许直接带着后代移动，否则已保存的后代层级会与真实路径不一致。
 */
@Service
@RequiredArgsConstructor
public class BusinessCategoryService {

    private static final int MAX_LEVEL = 3;

    private final BusinessCategoryDao businessCategoryDao;

    private final BusinessCategoryReferenceDao businessCategoryReferenceDao;

    public List<BusinessCategoryEntity> all(String scope) {
        return businessCategoryDao
            .selectList(new LambdaQueryWrapper<BusinessCategoryEntity>().eq(BusinessCategoryEntity::getScope, scope)
                .orderByAsc(BusinessCategoryEntity::getSortOrder, BusinessCategoryEntity::getId));
    }

    public BusinessCategoryEntity require(Long categoryId) {
        var entity = categoryId == null ? null : businessCategoryDao.selectById(categoryId);
        if (entity == null) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        }
        return entity;
    }

    /** 读取可被客户 / 供应商引用的分类：必须存在、作用域一致且启用。 */
    public BusinessCategoryEntity requireSelectable(Long categoryId, BusinessCategoryScopeEnum scope) {
        var entity = require(categoryId);
        if (!scope.name().equals(entity.getScope())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        }
        if (!ScmEnableStatusEnum.ENABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_DISABLED);
        }
        return entity;
    }

    public List<BusinessCategoryTreeVO> tree(String scope) {
        var rows = all(parseScope(scope).name());
        Map<Long, BusinessCategoryTreeVO> map = new LinkedHashMap<>();
        for (var row : rows) {
            var vo = new BusinessCategoryTreeVO();
            BeanUtils.copyProperties(row, vo);
            vo.setCategoryId(row.getId());
            vo.setChildren(new ArrayList<>());
            map.put(row.getId(), vo);
        }
        List<BusinessCategoryTreeVO> roots = new ArrayList<>();
        var byId = indexById(rows);
        for (var vo : map.values()) {
            vo.setCategoryPath(path(vo.getCategoryId(), byId));
            if (vo.getParentId() == null || !map.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                map.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    public BusinessCategoryVO detail(Long categoryId) {
        var entity = require(categoryId);
        var vo = new BusinessCategoryVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCategoryId(entity.getId());
        vo.setCategoryPath(path(entity.getId(), indexById(all(entity.getScope()))));
        return vo;
    }

    public static String path(Long categoryId, List<BusinessCategoryEntity> rows) {
        return path(categoryId, indexById(rows));
    }

    public static Map<Long, BusinessCategoryEntity> indexById(List<BusinessCategoryEntity> rows) {
        Map<Long, BusinessCategoryEntity> map = new HashMap<>();
        rows.forEach(category -> map.put(category.getId(), category));
        return map;
    }

    public static String path(Long categoryId, Map<Long, BusinessCategoryEntity> byId) {
        List<String> names = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long current = categoryId;
        while (current != null && seen.add(current) && byId.containsKey(current)) {
            var row = byId.get(current);
            names.add(0, row.getName());
            current = row.getParentId();
        }
        return String.join(" / ", names);
    }

    /** 返回分类及其全部后代，用于阻止把分类挂到自己的子树下。 */
    public List<Long> descendantIds(Long categoryId, List<BusinessCategoryEntity> rows) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (var row : rows) {
            if (row.getParentId() != null) {
                children.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        result.add(categoryId);
        pending.add(categoryId);
        while (!pending.isEmpty()) {
            for (var child : children.getOrDefault(pending.poll(), List.of())) {
                if (result.add(child)) {
                    pending.add(child);
                }
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public Long add(BusinessCategoryAddForm form) {
        BusinessCategoryScopeEnum scope = parseScope(form.getScope());
        lockParent(form.getParentId(), scope, null);
        int level = resolveLevel(form.getParentId(), null, scope);
        assertUnique(scope, form.getCategoryCode(), form.getName(), null);
        var entity = new BusinessCategoryEntity();
        apply(entity, form, scope, level);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedAt(entity.getUpdatedAt());
        entity.setCreatedBy(entity.getUpdatedBy());
        try {
            businessCategoryDao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_CODE_DUPLICATE);
        }
        return entity.getId();
    }

    @Transactional
    public void update(BusinessCategoryUpdateForm form) {
        var entity = require(form.getCategoryId());
        BusinessCategoryScopeEnum scope = parseScope(form.getScope());
        if (!scope.name().equals(entity.getScope())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_SCOPE_INVALID);
        }
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        var descendants = descendantIds(entity.getId(), all(scope.name()));
        if (form.getParentId() != null && descendants.contains(form.getParentId())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
        lockParent(form.getParentId(), scope, entity.getId());
        int level = resolveLevel(form.getParentId(), entity.getId(), scope);
        if (level != entity.getLevel() && descendants.size() > 1) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
        assertUnique(scope, form.getCategoryCode(), form.getName(), entity.getId());
        apply(entity, form, scope, level);
        entity.setVersion(form.getVersion());
        try {
            if (businessCategoryDao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_CODE_DUPLICATE);
        }
    }

    @Transactional
    public void delete(BusinessCategoryDeleteForm form) {
        var entity = businessCategoryDao.selectOne(new LambdaQueryWrapper<BusinessCategoryEntity>()
            .eq(BusinessCategoryEntity::getId, form.getCategoryId()).last("FOR UPDATE"));
        if (entity == null) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        }
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (businessCategoryDao.selectCount(new LambdaQueryWrapper<BusinessCategoryEntity>()
            .eq(BusinessCategoryEntity::getParentId, entity.getId())) > 0) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_HAS_CHILDREN);
        }
        if (businessCategoryReferenceDao.countCustomers(entity.getId()) > 0
            || businessCategoryReferenceDao.countSuppliers(entity.getId()) > 0) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_IN_USE);
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
        if (businessCategoryDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        businessCategoryDao.deleteById(entity.getId());
    }

    private void lockParent(Long parentId, BusinessCategoryScopeEnum scope, Long self) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(self)) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
        var parent = businessCategoryDao.selectOne(new LambdaQueryWrapper<BusinessCategoryEntity>()
            .eq(BusinessCategoryEntity::getId, parentId).last("FOR UPDATE"));
        if (parent == null || !scope.name().equals(parent.getScope())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
    }

    private int resolveLevel(Long parentId, Long self, BusinessCategoryScopeEnum scope) {
        if (parentId == null) {
            return 1;
        }
        if (parentId.equals(self)) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
        var parent = require(parentId);
        if (!scope.name().equals(parent.getScope()) || !ScmEnableStatusEnum.ENABLED.name().equals(parent.getStatus())) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_PARENT_INVALID);
        }
        if (parent.getLevel() >= MAX_LEVEL) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_LEVEL_INVALID);
        }
        return parent.getLevel() + 1;
    }

    private void assertUnique(BusinessCategoryScopeEnum scope, String categoryCode, String name, Long excludeId) {
        if (countByCode(scope, categoryCode, excludeId) > 0) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_CODE_DUPLICATE);
        }
        if (countByName(scope, name, excludeId) > 0) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_NAME_DUPLICATE);
        }
    }

    private long countByCode(BusinessCategoryScopeEnum scope, String categoryCode, Long excludeId) {
        var wrapper = new LambdaQueryWrapper<BusinessCategoryEntity>()
            .eq(BusinessCategoryEntity::getScope, scope.name())
            .eq(BusinessCategoryEntity::getCategoryCode, categoryCode.trim());
        if (excludeId != null) {
            wrapper.ne(BusinessCategoryEntity::getId, excludeId);
        }
        return businessCategoryDao.selectCount(wrapper);
    }

    private long countByName(BusinessCategoryScopeEnum scope, String name, Long excludeId) {
        var wrapper = new LambdaQueryWrapper<BusinessCategoryEntity>()
            .eq(BusinessCategoryEntity::getScope, scope.name()).eq(BusinessCategoryEntity::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(BusinessCategoryEntity::getId, excludeId);
        }
        return businessCategoryDao.selectCount(wrapper);
    }

    private void apply(BusinessCategoryEntity entity, BusinessCategoryAddForm form, BusinessCategoryScopeEnum scope,
        int level) {
        entity.setScope(scope.name());
        entity.setCategoryCode(form.getCategoryCode().trim());
        entity.setName(form.getName().trim());
        entity.setParentId(form.getParentId());
        entity.setLevel(level);
        entity.setSortOrder(form.getSortOrder());
        entity.setStatus(form.getStatus());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
    }

    private BusinessCategoryScopeEnum parseScope(String scope) {
        if (scope == null) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_SCOPE_INVALID);
        }
        try {
            return BusinessCategoryScopeEnum.valueOf(scope);
        } catch (IllegalArgumentException e) {
            throw new ScmBusinessException(BusinessCategoryErrorCode.BUSINESS_CATEGORY_SCOPE_INVALID);
        }
    }
}
