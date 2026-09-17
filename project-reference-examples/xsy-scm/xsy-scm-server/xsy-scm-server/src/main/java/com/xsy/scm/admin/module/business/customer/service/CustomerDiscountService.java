package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.constant.DiscountScopeEnum;
import com.xsy.scm.admin.module.business.customer.constant.DiscountStatusEnum;
import com.xsy.scm.admin.module.business.customer.dao.CustomerDiscountDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerDiscountEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerDiscountVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerDiscountManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 客户折扣率（计算折前价） Service
 *
 * <p>对标蔬东坡 17.5：统一折扣 / 按商品 / 按分类三维度。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerDiscountService {

    @Resource
    private CustomerDiscountDao customerDiscountDao;

    @Resource
    private CustomerDiscountManager customerDiscountManager;

    /**
     * 分页查询客户折扣率
     */
    public ResponseDTO<PageResult<CustomerDiscountVO>> query(CustomerDiscountQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerDiscountVO> list = customerDiscountDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增客户折扣率
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerDiscountAddForm addForm) {
        ResponseDTO<String> validateResult = validateForm(addForm);
        if (validateResult != null) {
            return validateResult;
        }
        CustomerDiscountEntity entity = buildEntity(addForm);
        entity.setDeletedFlag(Boolean.FALSE);
        if (entity.getStatus() == null) {
            entity.setStatus(DiscountStatusEnum.EFFECTIVE.getValue());
        }
        customerDiscountManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新客户折扣率
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerDiscountUpdateForm updateForm) {
        ResponseDTO<String> validateResult = validateForm(updateForm);
        if (validateResult != null) {
            return validateResult;
        }
        CustomerDiscountEntity entity = buildEntity(updateForm);
        entity.setDiscountId(updateForm.getDiscountId());
        customerDiscountManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除客户折扣率（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long discountId) {
        customerDiscountDao.batchUpdateDeleted(Collections.singletonList(discountId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除客户折扣率（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        customerDiscountDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 校验折扣范围与折扣率
     */
    private ResponseDTO<String> validateForm(CustomerDiscountAddForm form) {
        BigDecimal rate = form.getDiscountRate();
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            return ResponseDTO.userErrorParam("折扣率必须在 0~1 之间");
        }
        if (DiscountScopeEnum.PRODUCT.getValue().equals(form.getScopeType()) && form.getProductId() == null) {
            return ResponseDTO.userErrorParam("按商品折扣必须选择商品");
        }
        if (DiscountScopeEnum.CATEGORY.getValue().equals(form.getScopeType()) && form.getCategoryId() == null) {
            return ResponseDTO.userErrorParam("按分类折扣必须选择分类");
        }
        return null;
    }

    /**
     * 构建实体；统一折扣时清空商品 / 分类，保证维度单一
     */
    private CustomerDiscountEntity buildEntity(CustomerDiscountAddForm form) {
        CustomerDiscountEntity entity = SmartBeanUtil.copy(form, CustomerDiscountEntity.class);
        if (DiscountScopeEnum.UNIFIED.getValue().equals(form.getScopeType())) {
            entity.setProductId(null);
            entity.setCategoryId(null);
        } else if (DiscountScopeEnum.PRODUCT.getValue().equals(form.getScopeType())) {
            entity.setCategoryId(null);
        } else if (DiscountScopeEnum.CATEGORY.getValue().equals(form.getScopeType())) {
            entity.setProductId(null);
        }
        return entity;
    }
}
