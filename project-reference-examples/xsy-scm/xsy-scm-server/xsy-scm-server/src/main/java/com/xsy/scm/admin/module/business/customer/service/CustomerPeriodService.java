package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerPeriodDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerPeriodEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerPeriodVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerPeriodManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 客户账期 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerPeriodService {

    @Resource
    private CustomerPeriodDao periodDao;

    @Resource
    private CustomerPeriodManager periodManager;

    /**
     * 分页查询客户账期（按客户维度）
     */
    public ResponseDTO<PageResult<CustomerPeriodVO>> query(CustomerPeriodQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerPeriodVO> list = periodDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增客户账期
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerPeriodAddForm addForm) {
        CustomerPeriodEntity entity = SmartBeanUtil.copy(addForm, CustomerPeriodEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        periodManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新客户账期
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerPeriodUpdateForm updateForm) {
        CustomerPeriodEntity entity = SmartBeanUtil.copy(updateForm, CustomerPeriodEntity.class);
        periodManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除客户账期（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long periodId) {
        periodDao.batchUpdateDeleted(Collections.singletonList(periodId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除客户账期（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        periodDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
