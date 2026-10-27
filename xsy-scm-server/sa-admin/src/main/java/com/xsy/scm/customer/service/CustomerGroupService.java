package com.xsy.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.constant.CustomerErrorCode;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerGroupDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.entity.CustomerGroupEntity;
import com.xsy.scm.customer.domain.form.CustomerGroupAddForm;
import com.xsy.scm.customer.domain.form.CustomerGroupDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerGroupQueryForm;
import com.xsy.scm.customer.domain.form.CustomerGroupUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerGroupVO;
import com.xsy.scm.customer.manager.CustomerTypeValidator;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

/**
 * 客户分组读写与成员维护。
 *
 * <p>
 * 分组是价格约定的载体：组内客户对「同一商品 + 同一计量单位」执行同一条分组价。 成员关系存在客户档案上，保存分组时整体替换；分组必须至少保留一个有效客户， 否则这条分组价永远解析不到。分组停用后不再参与取价，但已有成员关系保留。
 */
@Service
@RequiredArgsConstructor
public class CustomerGroupService {

    private final CustomerGroupDao customerGroupDao;

    private final CustomerDao customerDao;

    /** 下拉选项：只返回启用分组。 */
    public List<CustomerGroupVO> optionList() {
        return customerGroupDao
            .selectList(new LambdaQueryWrapper<CustomerGroupEntity>()
                .eq(CustomerGroupEntity::getStatus, ScmEnableStatusEnum.ENABLED.name())
                .orderByAsc(CustomerGroupEntity::getGroupName, CustomerGroupEntity::getId))
            .stream().map(CustomerGroupService::toVO).toList();
    }

    public PageResult<CustomerGroupVO> query(CustomerGroupQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.asc("group_name"), OrderItem.asc("id"));
        }
        return SmartPageUtil.convert2PageResult(page, customerGroupDao.queryPage(page, form));
    }

    public CustomerGroupVO detail(Long groupId) {
        var vo = customerGroupDao.detail(groupId);
        if (vo == null) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_NOT_FOUND);
        }
        vo.setMembers(customerDao.selectGroupMembers(groupId));
        return vo;
    }

    public CustomerGroupEntity require(Long groupId) {
        var entity = groupId == null ? null : customerGroupDao.selectById(groupId);
        if (entity == null) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_NOT_FOUND);
        }
        return entity;
    }

    /** 客户档案引用分组时要求分组存在且启用；停用分组不能挂新客户。 */
    public CustomerGroupEntity requireSelectable(Long groupId) {
        var entity = require(groupId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_DISABLED);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerGroupAddForm form) {
        String code = CustomerTypeValidator.normalizeCode(form.getGroupCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_CODE_DUPLICATE);
        }
        List<Long> customerIds = requireMembers(form.getCustomerIds());

        var entity = new CustomerGroupEntity();
        entity.setGroupCode(code);
        entity.setGroupName(CustomerTypeValidator.normalizeName(form.getGroupName()));
        entity.setStatus(form.getStatus());
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            customerGroupDao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_CODE_DUPLICATE);
        }
        customerDao.assignGroup(customerIds, entity.getId(), ScmOperator.current());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerGroupUpdateForm form) {
        customerGroupDao.lock(form.getGroupId());
        var entity = require(form.getGroupId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        String code = CustomerTypeValidator.normalizeCode(form.getGroupCode());
        if (existsCode(code, entity.getId())) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_CODE_DUPLICATE);
        }
        List<Long> customerIds = requireMembers(form.getCustomerIds());

        entity.setGroupCode(code);
        entity.setGroupName(CustomerTypeValidator.normalizeName(form.getGroupName()));
        entity.setStatus(form.getStatus());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (customerGroupDao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_CODE_DUPLICATE);
        }
        // 整体替换：先补/更新本次名单，再把不在名单里的旧成员移出。
        customerDao.assignGroup(customerIds, entity.getId(), ScmOperator.current());
        customerDao.clearGroupMembers(entity.getId(), customerIds, ScmOperator.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerGroupDeleteForm form) {
        customerGroupDao.lock(form.getGroupId());
        var entity = require(form.getGroupId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        // 成员关系不是业务事实：删除分组时把客户移出分组，避免留下指向已删除分组的外键。
        customerDao.clearGroupMembers(entity.getId(), List.of(), ScmOperator.current());
        if (customerGroupDao.softDelete(entity.getId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 返回去重后的客户 ID；同时校验客户存在，避免把分组建在已删除的客户上。 */
    private List<Long> requireMembers(List<Long> submitted) {
        var ids = submitted == null
            ? List.<Long>of()
            : List.copyOf(new LinkedHashSet<>(submitted.stream().filter(Objects::nonNull).toList()));
        if (ids.isEmpty()) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_GROUP_MEMBER_REQUIRED);
        }
        long found = customerDao.selectCount(new LambdaQueryWrapper<CustomerEntity>().in(CustomerEntity::getId, ids)
            .eq(CustomerEntity::getDeleted, false));
        if (found != ids.size()) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }
        return ids;
    }

    private boolean existsCode(String code, Long excludedId) {
        var wrapper = new LambdaQueryWrapper<CustomerGroupEntity>().eq(CustomerGroupEntity::getGroupCode, code);
        if (excludedId != null) {
            wrapper.ne(CustomerGroupEntity::getId, excludedId);
        }
        return customerGroupDao.selectCount(wrapper) > 0;
    }

    private static void stamp(CustomerGroupEntity entity, boolean create) {
        String operator = ScmOperator.current();
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(operator);
        if (create) {
            entity.setCreatedAt(entity.getUpdatedAt());
            entity.setCreatedBy(operator);
        }
    }

    private static CustomerGroupVO toVO(CustomerGroupEntity entity) {
        var vo = new CustomerGroupVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setGroupId(entity.getId());
        return vo;
    }
}
