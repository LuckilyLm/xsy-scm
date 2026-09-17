package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseItemDao;
import com.xsy.scm.admin.module.business.purchase.domain.bo.PurchaseGenerateOrderBO;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购单生成 Manager（事务编排：落采购单 + 采购明细）
 *
 * @author xsy-scm
 */
@Service
public class PurchaseOrderGenerateManager {

    @Resource
    private PurchaseOrderManager purchaseOrderManager;

    @Resource
    private PurchaseItemDao purchaseItemDao;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 按供应商分组落库采购单与明细
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrders(List<PurchaseGenerateOrderBO> orders) {
        for (PurchaseGenerateOrderBO bo : orders) {
            PurchaseOrderEntity order = new PurchaseOrderEntity();
            order.setPurchaseNo(serialNumberService.generate(SerialNumberIdEnum.PURCHASE_ORDER));
            order.setSupplierId(bo.getSupplierId());
            order.setTotalAmount(nullToZero(bo.getTotalAmount()));
            order.setStatus(PurchaseStatusEnum.PURCHASING.getValue());
            order.setDeletedFlag(Boolean.FALSE);
            purchaseOrderManager.save(order);
            for (PurchaseItemEntity item : bo.getItems()) {
                item.setPurchaseId(order.getPurchaseId());
                purchaseItemDao.insert(item);
            }
        }
    }

    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
