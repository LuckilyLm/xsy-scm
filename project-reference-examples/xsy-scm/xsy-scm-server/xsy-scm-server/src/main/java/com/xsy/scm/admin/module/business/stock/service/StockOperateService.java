package com.xsy.scm.admin.module.business.stock.service;

import cn.hutool.core.util.IdUtil;
import com.xsy.scm.admin.module.business.stock.constant.StockBizTypeEnum;
import com.xsy.scm.admin.module.business.stock.constant.StockFlowTypeEnum;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.admin.module.business.stock.manager.StockOperateManager;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.SmartRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 库存业务层（统一对外入口）
 *
 * <p>其它模块（采购 / 订单 / 报损报溢 / 盘点 / 规格转换）需要变动库存时，
 * 注入本 Service 并调用对应语义方法，禁止直接操作库存表。</p>
 *
 * @author xsy-scm
 */
@Service
public class StockOperateService {

    @Resource
    private StockOperateManager stockOperateManager;

    /**
     * 统一执行库存变动：根据 flowType 自动分发到入库 / 出库 / 盘点调整
     */
    public void operate(StockOperateForm form) {
        if (form.getFlowNo() == null) {
            form.setFlowNo(genFlowNo());
        }
        if (form.getOperateBy() == null) {
            form.setOperateBy(SmartRequestUtil.getRequestUserId());
        }
        int ft = form.getFlowType();
        if (ft == StockFlowTypeEnum.PURCHASE_IN.getValue()
                || ft == StockFlowTypeEnum.RETURN_IN.getValue()
                || ft == StockFlowTypeEnum.OVERFLOW.getValue()
                || ft == StockFlowTypeEnum.CONVERT_IN.getValue()) {
            stockOperateManager.inbound(form);
        } else if (ft == StockFlowTypeEnum.SALE_OUT.getValue()
                || ft == StockFlowTypeEnum.LOSS.getValue()
                || ft == StockFlowTypeEnum.CONVERT_OUT.getValue()) {
            stockOperateManager.outbound(form);
        } else if (ft == StockFlowTypeEnum.CHECK_ADJUST.getValue()) {
            stockOperateManager.checkAdjust(form);
        } else {
            throw new BusinessException("不支持的库存流水类型：" + ft);
        }
    }

    /**
     * 采购入库
     */
    public void purchaseInbound(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.PURCHASE_IN.getValue());
        form.setBizType(StockBizTypeEnum.PURCHASE.getValue());
        operate(form);
    }

    /**
     * 销售出库
     */
    public void saleOutbound(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.SALE_OUT.getValue());
        form.setBizType(StockBizTypeEnum.ORDER.getValue());
        operate(form);
    }

    /**
     * 退货入库
     */
    public void returnInbound(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.RETURN_IN.getValue());
        form.setBizType(StockBizTypeEnum.ORDER.getValue());
        operate(form);
    }

    /**
     * 报损出库
     */
    public void lossOut(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.LOSS.getValue());
        form.setBizType(StockBizTypeEnum.ADJUST.getValue());
        operate(form);
    }

    /**
     * 报溢入库
     */
    public void overflowIn(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.OVERFLOW.getValue());
        form.setBizType(StockBizTypeEnum.ADJUST.getValue());
        operate(form);
    }

    /**
     * 盘点调整（form.quantity / form.weight 为目标绝对值）
     */
    public void checkAdjust(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.CHECK_ADJUST.getValue());
        form.setBizType(StockBizTypeEnum.CHECK.getValue());
        operate(form);
    }

    /**
     * 规格转换出
     */
    public void convertOut(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.CONVERT_OUT.getValue());
        form.setBizType(StockBizTypeEnum.CONVERT.getValue());
        operate(form);
    }

    /**
     * 规格转换入
     */
    public void convertIn(StockOperateForm form) {
        form.setFlowType(StockFlowTypeEnum.CONVERT_IN.getValue());
        form.setBizType(StockBizTypeEnum.CONVERT.getValue());
        operate(form);
    }

    private String genFlowNo() {
        return "STK" + IdUtil.getSnowflakeNextIdStr();
    }
}
