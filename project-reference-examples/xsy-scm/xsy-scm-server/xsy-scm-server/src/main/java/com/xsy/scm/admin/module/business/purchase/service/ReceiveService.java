package com.xsy.scm.admin.module.business.purchase.service;

import com.xsy.scm.admin.module.business.purchase.constant.PurchaseItemStatusEnum;
import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.admin.module.business.purchase.constant.ReceiveFlagEnum;
import com.xsy.scm.admin.module.business.purchase.constant.ReceiveStatusEnum;
import com.xsy.scm.admin.module.business.purchase.constant.ReceiveTypeEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseItemDao;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.admin.module.business.purchase.dao.ReceiveDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.ReceiveEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveNoOrderAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveRelateForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.ReceiveVO;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseItemManager;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseOrderManager;
import com.xsy.scm.admin.module.business.purchase.manager.ReceiveManager;
import com.xsy.scm.admin.module.business.stock.constant.StockBizTypeEnum;
import com.xsy.scm.admin.module.business.stock.constant.StockFlowTypeEnum;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.admin.module.business.stock.service.StockOperateService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import java.util.List;
import java.util.Objects;

/**
 * 采购收货单 Service
 *
 * <p>承担 Q2（多次收货逻辑2：一条采购明细可多次收货，每收一次生成一条记录）与
 * Q5（收货→入库：支持「直接入库」或「先收货、后入库确认」两步）的业务编排。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ReceiveService {

    @Resource
    private ReceiveDao receiveDao;

    @Resource
    private ReceiveManager receiveManager;

    @Resource
    private PurchaseItemDao itemDao;

    @Resource
    private PurchaseItemManager itemManager;

    @Resource
    private PurchaseOrderDao purchaseOrderDao;

    @Resource
    private PurchaseOrderManager purchaseOrderManager;

    @Resource
    private StockOperateService stockOperateService;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询收货单
     */
    public ResponseDTO<PageResult<ReceiveVO>> query(ReceiveQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ReceiveVO> list = receiveDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增收货单（Q2 逻辑2：一条采购明细可多次收货，每收一次生成一条记录）
     *
     * <p>同时：累计回写 {@code t_purchase_item.received_quantity}、按累计量打少/超收标记、
     * 推进明细与采购单状态，并按 Q5 开关决定是否收货即直接入库。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ReceiveAddForm addForm) {
        // 「采购中」为收货前置必经态：待接单必须先接单（转为采购中）才能收货（校验前置，避免写入孤儿记录）
        PurchaseItemEntity item = itemDao.selectById(addForm.getItemId());
        if (item == null) {
            throw new BusinessException("采购明细不存在");
        }
        PurchaseOrderEntity order = purchaseOrderDao.selectById(item.getPurchaseId());
        if (order == null) {
            throw new BusinessException("采购单不存在");
        }
        if (PurchaseStatusEnum.PENDING.getValue().equals(order.getStatus())) {
            throw new BusinessException("请先对采购单接单（转为采购中）后再收货");
        }

        ReceiveEntity entity = SmartBeanUtil.copy(addForm, ReceiveEntity.class);
        entity.setReceiveNo(serialNumberService.generate(SerialNumberIdEnum.RECEIVE));
        entity.setDeletedFlag(Boolean.FALSE);
        receiveManager.save(entity);

        // 累计已收（含本次），仅统计未删除且未作废的收货记录
        BigDecimal cumulative = receiveDao.queryByItemId(item.getItemId()).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeletedFlag()))
                .filter(r -> !ReceiveStatusEnum.INVALID.getValue().equals(r.getStatus()))
                .map(ReceiveEntity::getReceiveQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal requireQty = item.getRequireQuantity() != null ? item.getRequireQuantity()
                : (item.getPurchaseQuantity() != null ? item.getPurchaseQuantity() : BigDecimal.ZERO);

        // Q2 少/超收标记：累计 < 需求记少收，== 记正常，> 记超收
        int cmp = cumulative.compareTo(requireQty);
        Integer receiveFlag = cmp > 0 ? ReceiveFlagEnum.OVER.getValue()
                : (cmp < 0 ? ReceiveFlagEnum.UNDER.getValue() : ReceiveFlagEnum.NORMAL.getValue());

        // 回写累计收货量并推进明细状态
        item.setReceivedQuantity(cumulative);
        item.setStatus(resolveItemStatus(cumulative, requireQty));
        itemManager.update(item);

        // 推进采购单状态（只前进不回退）
        advancePurchaseOrder(item.getPurchaseId());

        // Q5 入库确认开关：directStock=true 收货即入库，否则仅「已收」待入库确认
        if (Boolean.TRUE.equals(addForm.getDirectStock())) {
            entity.setStatus(ReceiveStatusEnum.STOCKED.getValue());
            stockOperateService.purchaseInbound(buildStockForm(entity, item));
        } else {
            entity.setStatus(ReceiveStatusEnum.RECEIVED.getValue());
        }
        entity.setReceiveFlag(receiveFlag);
        receiveManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * Q5 入库确认：将「已收」的收货单置为「已入库」，并写入库存（余额 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> confirmInbound(Long receiveId) {
        ReceiveEntity receive = receiveDao.selectById(receiveId);
        if (receive == null || Boolean.TRUE.equals(receive.getDeletedFlag())) {
            throw new BusinessException("收货单不存在");
        }
        if (ReceiveStatusEnum.STOCKED.getValue().equals(receive.getStatus())) {
            return ResponseDTO.ok();
        }
        if (!ReceiveStatusEnum.RECEIVED.getValue().equals(receive.getStatus())) {
            throw new BusinessException("仅「已收」状态的收货单可入库确认");
        }
        PurchaseItemEntity item = itemDao.selectById(receive.getItemId());
        if (item == null) {
            throw new BusinessException("采购明细不存在");
        }
        stockOperateService.purchaseInbound(buildStockForm(receive, item));
        receive.setStatus(ReceiveStatusEnum.STOCKED.getValue());
        receiveManager.update(receive);
        return ResponseDTO.ok();
    }

    /**
     * 无单收货（现场收货，不关联采购单，对标蔬东坡 17.0）
     *
     * <p>无单收货同样必须生成入库流水、重算加权平均成本；后续可通过
     * {@link #relatePurchase(ReceiveRelateForm)} 补关联采购单。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> addNoOrder(ReceiveNoOrderAddForm addForm) {
        ReceiveEntity entity = SmartBeanUtil.copy(addForm, ReceiveEntity.class);
        entity.setReceiveNo(serialNumberService.generate(SerialNumberIdEnum.RECEIVE));
        entity.setReceiveType(ReceiveTypeEnum.NO_ORDER.getValue());
        entity.setReceiveFlag(ReceiveFlagEnum.NORMAL.getValue());
        if (entity.getReceiveTime() == null) {
            entity.setReceiveTime(LocalDateTime.now());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        boolean directStock = Boolean.TRUE.equals(addForm.getDirectStock());
        entity.setStatus(directStock ? ReceiveStatusEnum.STOCKED.getValue() : ReceiveStatusEnum.RECEIVED.getValue());
        receiveManager.save(entity);

        if (directStock) {
            stockOperateService.purchaseInbound(buildNoOrderStockForm(entity));
        }
        return ResponseDTO.ok();
    }

    /**
     * 无单收货 补关联采购单：挂到采购明细并回写累计收货量
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> relatePurchase(ReceiveRelateForm relateForm) {
        ReceiveEntity receive = receiveDao.selectById(relateForm.getReceiveId());
        if (receive == null || Boolean.TRUE.equals(receive.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("收货单不存在");
        }
        if (!ReceiveTypeEnum.NO_ORDER.getValue().equals(receive.getReceiveType())) {
            return ResponseDTO.userErrorParam("仅无单收货可补关联采购单");
        }
        PurchaseItemEntity item = itemDao.selectById(relateForm.getItemId());
        if (item == null) {
            return ResponseDTO.userErrorParam("采购明细不存在");
        }
        if (!item.getPurchaseId().equals(relateForm.getPurchaseId())) {
            return ResponseDTO.userErrorParam("采购明细与采购单不匹配");
        }

        receive.setPurchaseId(relateForm.getPurchaseId());
        receive.setItemId(relateForm.getItemId());
        receive.setReceiveType(ReceiveTypeEnum.PURCHASE.getValue());
        receiveManager.update(receive);

        // 回写累计收货量并推进明细 / 采购单状态
        BigDecimal cumulative = receiveDao.queryByItemId(item.getItemId()).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeletedFlag()))
                .filter(r -> !ReceiveStatusEnum.INVALID.getValue().equals(r.getStatus()))
                .map(ReceiveEntity::getReceiveQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requireQty = item.getRequireQuantity() != null ? item.getRequireQuantity()
                : (item.getPurchaseQuantity() != null ? item.getPurchaseQuantity() : BigDecimal.ZERO);
        int cmp = cumulative.compareTo(requireQty);
        receive.setReceiveFlag(cmp > 0 ? ReceiveFlagEnum.OVER.getValue()
                : (cmp < 0 ? ReceiveFlagEnum.UNDER.getValue() : ReceiveFlagEnum.NORMAL.getValue()));
        receiveManager.update(receive);

        item.setReceivedQuantity(cumulative);
        item.setStatus(resolveItemStatus(cumulative, requireQty));
        itemManager.update(item);
        advancePurchaseOrder(item.getPurchaseId());
        return ResponseDTO.ok();
    }

    /**
     * 更新收货单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ReceiveUpdateForm updateForm) {
        ReceiveEntity entity = SmartBeanUtil.copy(updateForm, ReceiveEntity.class);
        receiveManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除收货单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long receiveId) {
        receiveDao.batchUpdateDeleted(Collections.singletonList(receiveId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除收货单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        receiveDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    private Integer resolveItemStatus(BigDecimal cumulative, BigDecimal requireQty) {
        if (cumulative.compareTo(requireQty) >= 0) {
            return PurchaseItemStatusEnum.DONE.getValue();
        }
        if (cumulative.compareTo(BigDecimal.ZERO) > 0) {
            return PurchaseItemStatusEnum.PARTIAL.getValue();
        }
        return PurchaseItemStatusEnum.PENDING.getValue();
    }

    private void advancePurchaseOrder(Long purchaseId) {
        PurchaseOrderEntity order = purchaseOrderDao.selectById(purchaseId);
        if (order == null) {
            return;
        }
        List<PurchaseItemEntity> items = itemDao.queryByPurchaseId(purchaseId);
        boolean allDone = items.stream().allMatch(it -> {
            BigDecimal req = it.getRequireQuantity() != null ? it.getRequireQuantity()
                    : (it.getPurchaseQuantity() != null ? it.getPurchaseQuantity() : BigDecimal.ZERO);
            BigDecimal rec = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
            return req.compareTo(BigDecimal.ZERO) > 0 && rec.compareTo(req) >= 0;
        });
        boolean anyReceived = items.stream().anyMatch(it ->
                it.getReceivedQuantity() != null && it.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (allDone) {
            order.setStatus(PurchaseStatusEnum.COMPLETED.getValue());
        } else if (anyReceived && order.getStatus() < PurchaseStatusEnum.PARTIAL_RECEIVED.getValue()) {
            order.setStatus(PurchaseStatusEnum.PARTIAL_RECEIVED.getValue());
        } else {
            return;
        }
        purchaseOrderManager.update(order);
    }

    private StockOperateForm buildStockForm(ReceiveEntity receive, PurchaseItemEntity item) {
        StockOperateForm form = new StockOperateForm();
        form.setProductId(item.getProductId());
        form.setSkuId(item.getSkuId());
        form.setWarehouseId(1L);
        form.setQuantity(receive.getReceiveQuantity());
        form.setWeight(receive.getReceiveWeight() != null ? receive.getReceiveWeight() : BigDecimal.ZERO);
        form.setUnitPrice(receive.getUnitPrice());
        form.setBizId(receive.getReceiveId());
        form.setBizType(StockBizTypeEnum.PURCHASE.getValue());
        form.setFlowType(StockFlowTypeEnum.PURCHASE_IN.getValue());
        return form;
    }

    /**
     * 无单收货入库表单：商品 / 规格取自收货单自身
     */
    private StockOperateForm buildNoOrderStockForm(ReceiveEntity receive) {
        StockOperateForm form = new StockOperateForm();
        form.setProductId(receive.getProductId());
        form.setSkuId(receive.getSkuId());
        form.setWarehouseId(1L);
        form.setQuantity(receive.getReceiveQuantity());
        form.setWeight(receive.getReceiveWeight() != null ? receive.getReceiveWeight() : BigDecimal.ZERO);
        form.setUnitPrice(receive.getUnitPrice());
        form.setBizId(receive.getReceiveId());
        form.setBizType(StockBizTypeEnum.PURCHASE.getValue());
        form.setFlowType(StockFlowTypeEnum.PURCHASE_IN.getValue());
        return form;
    }
}
