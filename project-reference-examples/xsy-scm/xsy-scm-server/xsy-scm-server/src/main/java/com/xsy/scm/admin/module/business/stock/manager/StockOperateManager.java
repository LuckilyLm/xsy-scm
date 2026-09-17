package com.xsy.scm.admin.module.business.stock.manager;

import com.xsy.scm.admin.module.business.stock.constant.FlowDirectionEnum;
import com.xsy.scm.admin.module.business.stock.constant.StockOperateCodeEnum;
import com.xsy.scm.admin.module.business.stock.dao.StockBalanceDao;
import com.xsy.scm.admin.module.business.stock.dao.StockFlowDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockBalanceEntity;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockFlowEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.base.common.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 库存业务层（统一入口，事务内）
 *
 * <p>任何库存变动都必须经过本类，禁止其它模块 / Service 直接 UPDATE 余额。
 * 本类在单个事务内维护 {@code t_stock_balance} 并写入 {@code t_stock_flow}（含变动前后快照），
 * 满足「余额 + 流水」与可追溯审计要求。加权平均成本在每次入库时实时重算（06-04）。</p>
 *
 * @author xsy-scm
 */
@Service
public class StockOperateManager {

    @Resource
    private StockBalanceDao stockBalanceDao;

    @Resource
    private StockFlowDao stockFlowDao;

    /**
     * 入库：数量 / 重量增加，实时重算加权平均成本
     */
    @Transactional(rollbackFor = Exception.class)
    public void inbound(StockOperateForm form) {
        StockBalanceEntity balance = getOrCreateBalance(form);
        BigDecimal beforeQty = nz(balance.getQuantity());
        BigDecimal beforeWt = nz(balance.getWeight());
        BigDecimal beforeAvg = nz(balance.getAvgCost());
        BigDecimal qty = nz(form.getQuantity());
        BigDecimal wt = nz(form.getWeight());
        BigDecimal price = nz(form.getUnitPrice());

        BigDecimal beforeTotal = beforeQty.multiply(beforeAvg);
        BigDecimal addTotal = qty.multiply(price);
        BigDecimal afterQty = beforeQty.add(qty);
        BigDecimal afterAvg = afterQty.compareTo(BigDecimal.ZERO) > 0
                ? beforeTotal.add(addTotal).divide(afterQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal afterWt = beforeWt.add(wt);
        BigDecimal afterTotal = afterQty.multiply(afterAvg).setScale(2, RoundingMode.HALF_UP);

        balance.setQuantity(afterQty);
        balance.setWeight(afterWt);
        balance.setAvgCost(afterAvg);
        balance.setTotalCost(afterTotal);
        saveBalance(balance);

        writeFlow(form, FlowDirectionEnum.IN, beforeQty, afterQty, beforeAvg, afterAvg,
                price, qty.multiply(price).setScale(2, RoundingMode.HALF_UP), qty, wt);
    }

    /**
     * 出库：数量 / 重量扣减，成本不变（库存不足抛异常）
     */
    @Transactional(rollbackFor = Exception.class)
    public void outbound(StockOperateForm form) {
        StockBalanceEntity balance = stockBalanceDao.queryBySku(form.getSkuId(), wh(form));
        if (balance == null) {
            throw new BusinessException(StockOperateCodeEnum.BALANCE_NOT_EXIST);
        }
        BigDecimal beforeQty = nz(balance.getQuantity());
        BigDecimal beforeWt = nz(balance.getWeight());
        BigDecimal beforeAvg = nz(balance.getAvgCost());
        BigDecimal qty = nz(form.getQuantity());
        BigDecimal wt = nz(form.getWeight());
        if (beforeQty.compareTo(qty) < 0) {
            throw new BusinessException(StockOperateCodeEnum.STOCK_NOT_ENOUGH);
        }
        BigDecimal afterQty = beforeQty.subtract(qty);
        BigDecimal afterWt = beforeWt.subtract(wt);
        BigDecimal afterAvg = beforeAvg;
        BigDecimal afterTotal = afterQty.multiply(afterAvg).setScale(2, RoundingMode.HALF_UP);

        balance.setQuantity(afterQty);
        balance.setWeight(afterWt);
        balance.setTotalCost(afterTotal);
        saveBalance(balance);

        writeFlow(form, FlowDirectionEnum.OUT, beforeQty, afterQty, beforeAvg, afterAvg,
                beforeAvg, qty.multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP), qty, wt);
    }

    /**
     * 盘点调整：将余额直接置为目标绝对值，按差异方向生成流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkAdjust(StockOperateForm form) {
        StockBalanceEntity balance = getOrCreateBalance(form);
        BigDecimal beforeQty = nz(balance.getQuantity());
        BigDecimal beforeWt = nz(balance.getWeight());
        BigDecimal beforeAvg = nz(balance.getAvgCost());
        BigDecimal targetQty = nz(form.getQuantity());
        BigDecimal targetWt = nz(form.getWeight());
        BigDecimal diffQty = targetQty.subtract(beforeQty);
        BigDecimal diffWt = targetWt.subtract(beforeWt);
        FlowDirectionEnum direction = diffQty.compareTo(BigDecimal.ZERO) >= 0
                ? FlowDirectionEnum.IN : FlowDirectionEnum.OUT;

        balance.setQuantity(targetQty);
        balance.setWeight(targetWt);
        balance.setTotalCost(targetQty.multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP));
        saveBalance(balance);

        writeFlow(form, direction, beforeQty, targetQty, beforeAvg, beforeAvg,
                beforeAvg, diffQty.abs().multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP),
                diffQty.abs(), diffWt.abs());
    }

    /**
     * 查询并锁定（不存在则新建）库存余额
     */
    private StockBalanceEntity getOrCreateBalance(StockOperateForm form) {
        StockBalanceEntity balance = stockBalanceDao.queryBySku(form.getSkuId(), wh(form));
        if (balance == null) {
            balance = new StockBalanceEntity();
            balance.setProductId(form.getProductId());
            balance.setSkuId(form.getSkuId());
            balance.setWarehouseId(wh(form));
            balance.setBatchId(form.getBatchId());
            balance.setQuantity(BigDecimal.ZERO);
            balance.setWeight(BigDecimal.ZERO);
            balance.setAvgCost(BigDecimal.ZERO);
            balance.setTotalCost(BigDecimal.ZERO);
            balance.setDeletedFlag(Boolean.FALSE);
            balance.setCreateTime(LocalDateTime.now());
            balance.setUpdateTime(LocalDateTime.now());
            stockBalanceDao.insert(balance);
        }
        return balance;
    }

    private void saveBalance(StockBalanceEntity balance) {
        balance.setUpdateTime(LocalDateTime.now());
        stockBalanceDao.updateById(balance);
    }

    private void writeFlow(StockOperateForm form, FlowDirectionEnum direction,
                           BigDecimal beforeQty, BigDecimal afterQty,
                           BigDecimal beforeAvg, BigDecimal afterAvg,
                           BigDecimal unitPrice, BigDecimal amount,
                           BigDecimal flowQty, BigDecimal flowWt) {
        StockFlowEntity flow = new StockFlowEntity();
        flow.setFlowNo(form.getFlowNo());
        flow.setProductId(form.getProductId());
        flow.setSkuId(form.getSkuId());
        flow.setWarehouseId(wh(form));
        flow.setBatchId(form.getBatchId());
        flow.setFlowType(form.getFlowType());
        flow.setBizType(form.getBizType());
        flow.setBizId(form.getBizId());
        flow.setDirection(direction.getValue());
        flow.setQuantity(flowQty);
        flow.setWeight(flowWt);
        flow.setUnitPrice(nz(unitPrice));
        flow.setAmount(nz(amount));
        flow.setBeforeQuantity(beforeQty);
        flow.setAfterQuantity(afterQty);
        flow.setBeforeAvgCost(beforeAvg);
        flow.setAfterAvgCost(afterAvg);
        flow.setOperateBy(form.getOperateBy());
        flow.setOperateTime(LocalDateTime.now());
        flow.setCreateTime(LocalDateTime.now());
        flow.setUpdateTime(LocalDateTime.now());
        stockFlowDao.insert(flow);
    }

    private Long wh(StockOperateForm form) {
        return form.getWarehouseId() == null ? 1L : form.getWarehouseId();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
