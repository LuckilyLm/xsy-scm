package com.xsy.scm.admin.module.business.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseOrderManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.exception.BusinessException;
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
 * 采购单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class PurchaseOrderService {

    @Resource
    private PurchaseOrderDao purchaseOrderDao;

    @Resource
    private PurchaseOrderManager purchaseOrderManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询采购单
     */
    public ResponseDTO<PageResult<PurchaseOrderVO>> query(PurchaseOrderQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PurchaseOrderVO> list = purchaseOrderDao.queryPage(page, queryForm);
        PageResult<PurchaseOrderVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 新增采购单（待接单态）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(PurchaseOrderAddForm addForm) {
        PurchaseOrderEntity purchaseOrderEntity = SmartBeanUtil.copy(addForm, PurchaseOrderEntity.class);
        purchaseOrderEntity.setPurchaseNo(serialNumberService.generate(SerialNumberIdEnum.PURCHASE_ORDER));
        purchaseOrderEntity.setStatus(PurchaseStatusEnum.PENDING.getValue());
        purchaseOrderEntity.setDeletedFlag(Boolean.FALSE);
        purchaseOrderManager.save(purchaseOrderEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新采购单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(PurchaseOrderUpdateForm updateForm) {
        PurchaseOrderEntity purchaseOrderEntity = SmartBeanUtil.copy(updateForm, PurchaseOrderEntity.class);
        purchaseOrderManager.update(purchaseOrderEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除采购单（逻辑删除，仅供异常数据清理）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long purchaseId) {
        purchaseOrderDao.batchUpdateDeleted(Collections.singletonList(purchaseId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除采购单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        purchaseOrderDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 接单：待接单（1）→ 采购中（2），进入「采购中→部分收货→已完成」流转
     *
     * <p>幂等：已非待接单（如已是采购中/部分收货/已完成）直接返回成功，便于批量重试。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> accept(Long purchaseId) {
        PurchaseOrderEntity order = purchaseOrderDao.selectById(purchaseId);
        if (order == null || Boolean.TRUE.equals(order.getDeletedFlag())) {
            throw new BusinessException("采购单不存在");
        }
        if (!PurchaseStatusEnum.PENDING.getValue().equals(order.getStatus())) {
            return ResponseDTO.ok();
        }
        order.setStatus(PurchaseStatusEnum.PURCHASING.getValue());
        purchaseOrderManager.update(order);
        return ResponseDTO.ok();
    }
}
