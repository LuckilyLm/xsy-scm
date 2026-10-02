package com.xsy.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.customer.domain.form.CustomerTypeAddForm;
import com.xsy.scm.customer.domain.form.CustomerTypeDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerTypeQueryForm;
import com.xsy.scm.customer.domain.form.CustomerTypeUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerTypeVO;
import com.xsy.scm.customer.manager.CustomerTypeValidator;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_CODE_DUPLICATE;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_IN_USE;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_NOT_FOUND;

/**
 * 客户类型读写。
 *
 * <p>
 * 客户类型是「可维护字典」：编码唯一、名称允许重复、没有独立的状态端点。
 */
@Service
@RequiredArgsConstructor
public class CustomerTypeService {

    /**
     * 排序白名单：客户端只能选择这些列，避免把任意字段拼入 SQL。
     */
    private static final Set<String> SORTABLE = Set.of("type_code", "name", "status", "updated_at");

    private final CustomerTypeDao customerTypeDao;

    private final CustomerDao customerDao;
    private final CustomerSkuVisibilityDao customerSkuVisibilityDao;
    private final CustomerTypePriceDao customerTypePriceDao;

    /**
     * 全量客户类型（含 DISABLED），供内部逻辑使用。
     */
    public List<CustomerTypeEntity> all() {
        return customerTypeDao.selectList(new LambdaQueryWrapper<CustomerTypeEntity>()
                .orderByAsc(CustomerTypeEntity::getName, CustomerTypeEntity::getId));
    }

    /**
     * 读取客户类型，不存在或已删除 → 40431。
     */
    public CustomerTypeEntity require(Long typeId) {
        CustomerTypeEntity entity = typeId == null ? null : customerTypeDao.selectById(typeId);
        if (entity == null) {
            throw new ScmBusinessException(CUSTOMER_TYPE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 读取「可用于新建 / 编辑客户」的类型：必须存在、未删除且 {@code ENABLED}。
     *
     * <p>
     * 停用类型不允许被新引用，但已引用它的客户仍然可读、可改其它字段。
     */
    public CustomerTypeEntity requireSelectableType(Long typeId) {
        CustomerTypeEntity entity = require(typeId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(CUSTOMER_TYPE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 下拉选项：只返回 {@code ENABLED}，按名称排序。
     */
    public List<CustomerTypeVO> optionList() {
        return customerTypeDao
                .selectList(new LambdaQueryWrapper<CustomerTypeEntity>()
                        .eq(CustomerTypeEntity::getStatus, ScmEnableStatusEnum.ENABLED.name())
                        .orderByAsc(CustomerTypeEntity::getName, CustomerTypeEntity::getId))
                .stream().map(CustomerTypeService::toVO).toList();
    }

    public PageResult<CustomerTypeVO> query(CustomerTypeQueryForm form) {
        assertSortable(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.asc("name"), OrderItem.asc("id"));
        }
        List<CustomerTypeEntity> rows = customerTypeDao.queryPage(page, form);
        List<CustomerTypeVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerTypeAddForm form) {
        CustomerTypeValidator.validateRequired(form);
        String code = CustomerTypeValidator.normalizeCode(form.getTypeCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(CUSTOMER_TYPE_CODE_DUPLICATE);
        }
        CustomerTypeEntity entity = new CustomerTypeEntity();
        entity.setTypeCode(code);
        entity.setName(CustomerTypeValidator.normalizeName(form.getName()));
        entity.setStatus(form.getStatus());
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            customerTypeDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发兜底：显式查重与插入之间存在窗口
            throw new ScmBusinessException(CUSTOMER_TYPE_CODE_DUPLICATE);
        }
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerTypeUpdateForm form) {
        CustomerTypeValidator.validateRequired(form);
        customerTypePriceDao.lockParent(form.getTypeId());
        CustomerTypeEntity entity = require(form.getTypeId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        String code = CustomerTypeValidator.normalizeCode(form.getTypeCode());
        if (existsCode(code, form.getTypeId())) {
            throw new ScmBusinessException(CUSTOMER_TYPE_CODE_DUPLICATE);
        }
        entity.setTypeCode(code);
        entity.setName(CustomerTypeValidator.normalizeName(form.getName()));
        entity.setStatus(form.getStatus());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (customerTypeDao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CUSTOMER_TYPE_CODE_DUPLICATE);
        }
    }

    /**
     * 删除客户类型。
     *
     * <p>
     * 被活动客户引用时拒绝删除—— 否则会把存量客户指向一个不存在的类型。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerTypeDeleteForm form) {
        customerTypePriceDao.lockParent(form.getTypeId());
        CustomerTypeEntity entity = require(form.getTypeId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (customerDao.countActiveByTypeId(form.getTypeId()) > 0
                || customerSkuVisibilityDao.typeReferences(form.getTypeId()) > 0) {
            throw new ScmBusinessException(CUSTOMER_TYPE_IN_USE);
        }
        if (customerTypeDao.softDelete(form.getTypeId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<CustomerTypeEntity> wrapper = new LambdaQueryWrapper<CustomerTypeEntity>()
                .eq(CustomerTypeEntity::getTypeCode, code);
        if (excludeId != null) {
            wrapper.ne(CustomerTypeEntity::getId, excludeId);
        }
        return customerTypeDao.selectCount(wrapper) > 0;
    }

    private void assertSortable(CustomerTypeQueryForm form) {
        if (form.getSortItemList() == null) {
            return;
        }
        boolean illegal = form.getSortItemList().stream()
                .anyMatch(item -> item.getColumn() == null || !SORTABLE.contains(item.getColumn().toLowerCase()));
        if (illegal) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private void stamp(CustomerTypeEntity entity, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operator);
        }
    }

    private static CustomerTypeVO toVO(CustomerTypeEntity entity) {
        CustomerTypeVO vo = new CustomerTypeVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setTypeId(entity.getId());
        return vo;
    }
}
