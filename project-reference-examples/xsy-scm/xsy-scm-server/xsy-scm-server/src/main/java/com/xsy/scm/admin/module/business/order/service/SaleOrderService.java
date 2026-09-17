package com.xsy.scm.admin.module.business.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.service.ReceivableService;
import com.xsy.scm.admin.module.business.order.constant.OrderOperateTypeEnum;
import com.xsy.scm.admin.module.business.order.constant.OrderStatusEnum;
import com.xsy.scm.admin.module.business.order.dao.SaleOrderDao;
import com.xsy.scm.admin.module.business.order.dao.SaleOrderItemDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderEntity;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderItemEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderVO;
import com.xsy.scm.admin.module.business.order.manager.SaleOrderManager;
import com.xsy.scm.admin.module.business.order.service.SaleOrderLogService;
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
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 销售订单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SaleOrderService {

    @Resource
    private SaleOrderDao saleOrderDao;

    @Resource
    private SaleOrderManager saleOrderManager;

    @Resource
    private SerialNumberService serialNumberService;

    @Resource
    private SaleOrderItemDao saleOrderItemDao;

    @Resource
    private StockOperateService stockOperateService;

    @Resource
    private SaleOrderLogService saleOrderLogService;

    @Resource
    private ReceivableService receivableService;

    /**
     * 分页查询销售订单
     */
    public ResponseDTO<PageResult<SaleOrderVO>> query(SaleOrderQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SaleOrderVO> list = saleOrderDao.queryPage(page, queryForm);
        PageResult<SaleOrderVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 新增销售订单（草稿态，金额由后续核算填充）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SaleOrderAddForm addForm) {
        SaleOrderEntity saleOrderEntity = SmartBeanUtil.copy(addForm, SaleOrderEntity.class);
        saleOrderEntity.setOrderNo(serialNumberService.generate(SerialNumberIdEnum.SALE_ORDER));
        saleOrderEntity.setStatus(OrderStatusEnum.DRAFT.getValue());
        saleOrderEntity.setDeletedFlag(Boolean.FALSE);
        saleOrderManager.save(saleOrderEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新销售订单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SaleOrderUpdateForm updateForm) {
        SaleOrderEntity saleOrderEntity = SmartBeanUtil.copy(updateForm, SaleOrderEntity.class);
        saleOrderManager.update(saleOrderEntity);
        return ResponseDTO.ok();
    }

    /**
     * 确认订单：草稿 / 待确认 → 已确认，并写确认日志。
     * <p>已确认(3) 为发货的前置状态：deliver 仅放行 3 / 5 / 6，故此处是订单进入可发货态的入口。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> confirm(Long orderId) {
        SaleOrderEntity order = saleOrderDao.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Integer status = order.getStatus();
        if (!OrderStatusEnum.DRAFT.getValue().equals(status) && !OrderStatusEnum.PENDING.getValue().equals(status)) {
            throw new BusinessException("仅草稿或待确认订单可以确认");
        }
        order.setStatus(OrderStatusEnum.CONFIRMED.getValue());
        saleOrderManager.update(order);

        SaleOrderLogAddForm logForm = new SaleOrderLogAddForm();
        logForm.setOrderId(orderId);
        logForm.setOperateType(OrderOperateTypeEnum.CONFIRM.getValue());
        saleOrderLogService.add(logForm);

        return ResponseDTO.ok();
    }

    /**
     * 签收：配送中(7) → 已签收(8)，按核算金额生成应收（09-01 已定），并写签收日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> sign(Long orderId) {
        SaleOrderEntity order = saleOrderDao.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!OrderStatusEnum.DELIVERING.getValue().equals(order.getStatus())) {
            throw new BusinessException("仅配送中订单可以签收");
        }
        order.setStatus(OrderStatusEnum.SIGNED.getValue());
        saleOrderManager.update(order);

        receivableService.generateFromOrder(order);

        SaleOrderLogAddForm logForm = new SaleOrderLogAddForm();
        logForm.setOrderId(orderId);
        logForm.setOperateType(OrderOperateTypeEnum.SIGN.getValue());
        saleOrderLogService.add(logForm);

        return ResponseDTO.ok();
    }

    /**
     * 删除订单（逻辑删除，仅供草稿类数据清理）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long orderId) {
        saleOrderDao.batchUpdateDeleted(Collections.singletonList(orderId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除订单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        saleOrderDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 发货：触发销售出库（库存反向流水），订单置「配送中」并写发货日志。
     * <p>逐条明细调用统一库存业务层 {@code saleOutbound}（余额 + 流水，库存不足抛异常回滚）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> deliver(Long orderId) {
        SaleOrderEntity order = saleOrderDao.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Integer status = order.getStatus();
        if (OrderStatusEnum.DELIVERING.getValue().equals(status)
                || OrderStatusEnum.SIGNED.getValue().equals(status)
                || OrderStatusEnum.COMPLETED.getValue().equals(status)
                || OrderStatusEnum.REFUNDING.getValue().equals(status)
                || OrderStatusEnum.CANCELLED.getValue().equals(status)
                || OrderStatusEnum.INVALID.getValue().equals(status)) {
            throw new BusinessException("订单已发货或已结束，无法重复发货");
        }
        if (OrderStatusEnum.DRAFT.getValue().equals(status)
                || OrderStatusEnum.PENDING.getValue().equals(status)
                || OrderStatusEnum.PURCHASING.getValue().equals(status)) {
            throw new BusinessException("请先确认订单后再发货");
        }

        List<SaleOrderItemEntity> items = saleOrderItemDao.queryByOrderId(orderId);
        for (SaleOrderItemEntity item : items) {
            StockOperateForm form = new StockOperateForm();
            form.setProductId(item.getProductId());
            form.setSkuId(item.getSkuId());
            // 仓库：订单未单独记录仓库时沿用默认仓（与采购入库一致）
            form.setWarehouseId(1L);
            form.setQuantity(item.getQuantity());
            form.setWeight(item.getActualWeight() != null ? item.getActualWeight() : BigDecimal.ZERO);
            form.setBizId(item.getItemId());
            form.setBizType(StockBizTypeEnum.ORDER.getValue());
            form.setFlowType(StockFlowTypeEnum.SALE_OUT.getValue());
            stockOperateService.saleOutbound(form);
        }

        order.setStatus(OrderStatusEnum.DELIVERING.getValue());
        saleOrderManager.update(order);

        SaleOrderLogAddForm logForm = new SaleOrderLogAddForm();
        logForm.setOrderId(orderId);
        logForm.setOperateType(OrderOperateTypeEnum.DELIVER.getValue());
        saleOrderLogService.add(logForm);

        return ResponseDTO.ok();
    }
}
