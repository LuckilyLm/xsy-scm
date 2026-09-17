package com.xsy.scm.admin.module.business.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.constant.SupplierApplyStatusEnum;
import com.xsy.scm.admin.module.business.supplier.dao.SupplierProductApplyDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierProductApplyEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyAuditForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierProductApplyVO;
import com.xsy.scm.admin.module.business.supplier.manager.SupplierProductApplyManager;
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
 * 供应商商品提报 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SupplierProductApplyService {

    @Resource
    private SupplierProductApplyDao supplierProductApplyDao;

    @Resource
    private SupplierProductApplyManager supplierProductApplyManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询供应商商品提报
     */
    public ResponseDTO<PageResult<SupplierProductApplyVO>> query(SupplierProductApplyQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SupplierProductApplyVO> list = supplierProductApplyDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增供应商商品提报，提报单号由编号生成器生成（SPB + 日期 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SupplierProductApplyAddForm addForm) {
        SupplierProductApplyEntity entity = SmartBeanUtil.copy(addForm, SupplierProductApplyEntity.class);
        entity.setApplyNo(serialNumberService.generate(SerialNumberIdEnum.SUPPLIER_PRODUCT_APPLY));
        entity.setAuditStatus(SupplierApplyStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        supplierProductApplyManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新供应商商品提报
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SupplierProductApplyUpdateForm updateForm) {
        SupplierProductApplyEntity entity = SmartBeanUtil.copy(updateForm, SupplierProductApplyEntity.class);
        supplierProductApplyManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 审核供应商商品提报
     *
     * <p>仅待审核的提报可审核；审核通过后由 01 商品模块据此生成商品草稿（后续集成）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> audit(SupplierProductApplyAuditForm auditForm) {
        SupplierProductApplyEntity entity = supplierProductApplyDao.selectById(auditForm.getApplyId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("提报单不存在");
        }
        if (!SupplierApplyStatusEnum.PENDING.getValue().equals(entity.getAuditStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的提报可审核");
        }
        entity.setAuditStatus(auditForm.getAuditStatus());
        entity.setRejectReason(auditForm.getRejectReason());
        supplierProductApplyManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除供应商商品提报（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long applyId) {
        supplierProductApplyDao.batchUpdateDeleted(Collections.singletonList(applyId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除供应商商品提报（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        supplierProductApplyDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
