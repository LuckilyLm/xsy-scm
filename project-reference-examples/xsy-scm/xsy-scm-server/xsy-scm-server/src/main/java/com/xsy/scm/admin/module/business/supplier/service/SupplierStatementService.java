package com.xsy.scm.admin.module.business.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.constant.SupplierStatementStatusEnum;
import com.xsy.scm.admin.module.business.supplier.dao.SupplierStatementDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierStatementEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementConfirmForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierStatementVO;
import com.xsy.scm.admin.module.business.supplier.manager.SupplierStatementManager;
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
 * 供应商对账单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SupplierStatementService {

    @Resource
    private SupplierStatementDao supplierStatementDao;

    @Resource
    private SupplierStatementManager supplierStatementManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询供应商对账单
     */
    public ResponseDTO<PageResult<SupplierStatementVO>> query(SupplierStatementQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SupplierStatementVO> list = supplierStatementDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增供应商对账单，对账单号由编号生成器生成（DZD + 日期 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SupplierStatementAddForm addForm) {
        SupplierStatementEntity entity = SmartBeanUtil.copy(addForm, SupplierStatementEntity.class);
        entity.setStatementNo(serialNumberService.generate(SerialNumberIdEnum.SUPPLIER_STATEMENT));
        entity.setStatus(SupplierStatementStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        supplierStatementManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新供应商对账单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SupplierStatementUpdateForm updateForm) {
        SupplierStatementEntity entity = SmartBeanUtil.copy(updateForm, SupplierStatementEntity.class);
        supplierStatementManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 供应商确认 / 驳回对账单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> confirm(SupplierStatementConfirmForm confirmForm) {
        SupplierStatementEntity entity = supplierStatementDao.selectById(confirmForm.getStatementId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("对账单不存在");
        }
        if (!SupplierStatementStatusEnum.PENDING.getValue().equals(entity.getStatus())
                && !SupplierStatementStatusEnum.CONFIRMED.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("当前状态不支持确认 / 驳回");
        }
        entity.setStatus(confirmForm.getStatus());
        supplierStatementManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 财务结算对账单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> settle(Long statementId) {
        SupplierStatementEntity entity = supplierStatementDao.selectById(statementId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("对账单不存在");
        }
        if (!SupplierStatementStatusEnum.CONFIRMED.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("仅供应商已确认的对账单可结算");
        }
        entity.setStatus(SupplierStatementStatusEnum.SETTLED.getValue());
        supplierStatementManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除供应商对账单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long statementId) {
        supplierStatementDao.batchUpdateDeleted(Collections.singletonList(statementId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除供应商对账单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        supplierStatementDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
