package com.xsy.scm.admin.module.business.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.dao.SupplierDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.SupplierEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.SupplierVO;
import com.xsy.scm.admin.module.business.purchase.manager.SupplierManager;
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
 * 供应商档案 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SupplierService {

    @Resource
    private SupplierDao supplierDao;

    @Resource
    private SupplierManager supplierManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询供应商
     */
    public ResponseDTO<PageResult<SupplierVO>> query(SupplierQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SupplierVO> list = supplierDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增供应商，编码由编号生成器生成（GYS + 6 位流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SupplierAddForm addForm) {
        SupplierEntity entity = SmartBeanUtil.copy(addForm, SupplierEntity.class);
        entity.setSupplierNo(serialNumberService.generate(SerialNumberIdEnum.SUPPLIER));
        entity.setDeletedFlag(Boolean.FALSE);
        supplierManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新供应商
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SupplierUpdateForm updateForm) {
        SupplierEntity entity = SmartBeanUtil.copy(updateForm, SupplierEntity.class);
        supplierManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除供应商（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long supplierId) {
        supplierDao.batchUpdateDeleted(Collections.singletonList(supplierId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除供应商（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        supplierDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 查询所有未删除供应商
     */
    public ResponseDTO<List<SupplierVO>> queryAll() {
        List<SupplierVO> list = supplierDao.queryAll();
        return ResponseDTO.ok(list);
    }
}
