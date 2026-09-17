package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 客户 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerService {

    @Resource
    private CustomerDao customerDao;

    @Resource
    private CustomerManager customerManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询客户
     */
    public ResponseDTO<PageResult<CustomerVO>> query(CustomerQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerVO> list = customerDao.queryPage(page, queryForm);
        PageResult<CustomerVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 新增客户
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerAddForm addForm) {
        CustomerEntity customerEntity = SmartBeanUtil.copy(addForm, CustomerEntity.class);
        if (customerEntity.getParentCustomerId() == null) {
            customerEntity.setParentCustomerId(0L);
        }
        customerEntity.setCustomerNo(serialNumberService.generate(SerialNumberIdEnum.CUSTOMER));
        customerEntity.setDeletedFlag(Boolean.FALSE);
        customerManager.save(customerEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新客户
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerUpdateForm updateForm) {
        CustomerEntity customerEntity = SmartBeanUtil.copy(updateForm, CustomerEntity.class);
        customerManager.update(customerEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除客户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long customerId) {
        customerDao.batchUpdateDeleted(Collections.singletonList(customerId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除客户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        customerDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 查询所有未删除客户（用于下拉选择）
     */
    public ResponseDTO<List<CustomerVO>> queryAll() {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerEntity::getDeletedFlag, Boolean.FALSE);
        wrapper.orderByAsc(CustomerEntity::getCustomerId);
        List<CustomerEntity> list = customerDao.selectList(wrapper);
        return ResponseDTO.ok(SmartBeanUtil.copyList(list, CustomerVO.class));
    }
}
