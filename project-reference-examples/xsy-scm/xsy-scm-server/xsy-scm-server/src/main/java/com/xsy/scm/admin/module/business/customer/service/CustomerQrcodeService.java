package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerQrcodeDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerQrcodeEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerQrcodeVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerQrcodeManager;
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
 * 业务员推广二维码 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerQrcodeService {

    @Resource
    private CustomerQrcodeDao qrcodeDao;

    @Resource
    private CustomerQrcodeManager qrcodeManager;

    /**
     * 分页查询推广二维码
     */
    public ResponseDTO<PageResult<CustomerQrcodeVO>> query(CustomerQrcodeQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerQrcodeVO> list = qrcodeDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增推广二维码
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerQrcodeAddForm addForm) {
        CustomerQrcodeEntity entity = SmartBeanUtil.copy(addForm, CustomerQrcodeEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        qrcodeManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新推广二维码
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerQrcodeUpdateForm updateForm) {
        CustomerQrcodeEntity entity = SmartBeanUtil.copy(updateForm, CustomerQrcodeEntity.class);
        qrcodeManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除推广二维码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long qrcodeId) {
        qrcodeDao.batchUpdateDeleted(Collections.singletonList(qrcodeId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除推广二维码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        qrcodeDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
