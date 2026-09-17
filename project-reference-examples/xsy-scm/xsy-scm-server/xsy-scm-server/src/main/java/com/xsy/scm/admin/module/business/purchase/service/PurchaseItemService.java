package com.xsy.scm.admin.module.business.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseItemDao;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemAssignSupplierForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseItemVO;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseItemManager;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseOrderManager;
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
 * 采购明细 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class PurchaseItemService {

    @Resource
    private PurchaseItemDao itemDao;

    @Resource
    private PurchaseItemManager itemManager;

    @Resource
    private PurchaseOrderDao purchaseOrderDao;

    @Resource
    private PurchaseOrderManager purchaseOrderManager;

    /**
     * 分页查询采购明细（按采购单维度）
     */
    public ResponseDTO<PageResult<PurchaseItemVO>> query(PurchaseItemQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PurchaseItemVO> list = itemDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增采购明细
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(PurchaseItemAddForm addForm) {
        PurchaseItemEntity entity = SmartBeanUtil.copy(addForm, PurchaseItemEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        itemManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新采购明细
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(PurchaseItemUpdateForm updateForm) {
        PurchaseItemEntity entity = SmartBeanUtil.copy(updateForm, PurchaseItemEntity.class);
        itemManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除采购明细（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long itemId) {
        itemDao.batchUpdateDeleted(Collections.singletonList(itemId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除采购明细（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        itemDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 采购单改绑供应商（供应商分拣「实时分配」动作，对标蔬东坡 17.1）
     *
     * <p>当前为「一单一供应商」模型：改绑按整单生效，同步更新采购单与全部明细的绑定供应商。
     * 已完成 / 已取消的采购单不可改绑。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> reassignSupplier(PurchaseItemAssignSupplierForm form) {
        PurchaseOrderEntity order = purchaseOrderDao.selectById(form.getPurchaseId());
        if (order == null || Boolean.TRUE.equals(order.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("采购单不存在");
        }
        if (PurchaseStatusEnum.COMPLETED.getValue().equals(order.getStatus())
                || PurchaseStatusEnum.CANCELLED.getValue().equals(order.getStatus())) {
            return ResponseDTO.userErrorParam("已完成 / 已取消的采购单不可改绑供应商");
        }
        order.setSupplierId(form.getSupplierId());
        purchaseOrderManager.update(order);

        List<PurchaseItemEntity> items = itemDao.queryByPurchaseId(form.getPurchaseId());
        for (PurchaseItemEntity item : items) {
            item.setSupplierId(form.getSupplierId());
            itemManager.update(item);
        }
        return ResponseDTO.ok();
    }
}
