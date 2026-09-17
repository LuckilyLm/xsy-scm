package com.xsy.scm.admin.module.business.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.dao.SupplierManufacturerDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierManufacturerEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierManufacturerVO;
import com.xsy.scm.admin.module.business.supplier.manager.SupplierManufacturerManager;
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
 * 供应商厂商信息 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SupplierManufacturerService {

    @Resource
    private SupplierManufacturerDao supplierManufacturerDao;

    @Resource
    private SupplierManufacturerManager supplierManufacturerManager;

    /**
     * 分页查询供应商厂商信息
     */
    public ResponseDTO<PageResult<SupplierManufacturerVO>> query(SupplierManufacturerQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SupplierManufacturerVO> list = supplierManufacturerDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增供应商厂商信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SupplierManufacturerAddForm addForm) {
        SupplierManufacturerEntity entity = SmartBeanUtil.copy(addForm, SupplierManufacturerEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        supplierManufacturerManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新供应商厂商信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SupplierManufacturerUpdateForm updateForm) {
        SupplierManufacturerEntity entity = SmartBeanUtil.copy(updateForm, SupplierManufacturerEntity.class);
        supplierManufacturerManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除供应商厂商信息（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long manufacturerId) {
        supplierManufacturerDao.batchUpdateDeleted(Collections.singletonList(manufacturerId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除供应商厂商信息（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        supplierManufacturerDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
