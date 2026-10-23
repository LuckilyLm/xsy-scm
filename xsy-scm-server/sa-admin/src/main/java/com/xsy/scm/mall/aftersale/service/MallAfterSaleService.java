package com.xsy.scm.mall.aftersale.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleApplyForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.service.OrderReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城售后的客户侧写操作：申请与撤销。
 *
 * <p>
 * 两条路径都不新建售后状态机：申请走正式 {@link OrderReturnService} 的建单命令（仅已确认订单、累计申请不超过实数量），
 * 撤销走同一条取消命令（仅待审核）。归属由本服务在入口按登录客户判定，员工负责人范围不适用于商城身份。
 */
@Service
@RequiredArgsConstructor
public class MallAfterSaleService {
    private final MallOrderQueryService orderQueryService;
    private final MallAfterSaleQueryService afterSaleQueryService;
    private final OrderReturnService orderReturnService;

    /**
     * 客户申请售后。
     *
     * <p>
     * 归属先判；订单状态门槛（仅已确认）、可退数量上限（实数量减去已在途申请）与幂等都在正式退货建单路径里， 商城入口不放宽任何一条。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallAfterSaleVO apply(RequestMallCustomer customer, MallAfterSaleApplyForm form, String idempotencyKey) {
        orderQueryService.requireOwnedOrder(customer, form.getOrderId());
        OrderReturnAddForm addForm = new OrderReturnAddForm();
        addForm.setOrderId(form.getOrderId());
        addForm.setReason(form.getReason());
        addForm.setItems(form.getItems().stream().map(MallAfterSaleService::item).toList());
        OrderReturnDetailVO created = orderReturnService.createFromMall(addForm, idempotencyKey);
        return afterSaleQueryService.detail(customer, created.getReturnId());
    }

    /**
     * 客户撤销待审核的售后申请。
     *
     * <p>
     * 前置状态判定只为给出客户可读的原因；真正的门槛在正式取消命令（仅 {@code PENDING} 可撤销）。 处理意见固定为「客户自行撤销」，与后台驳回 / 取消的操作日志区分。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallAfterSaleVO cancel(RequestMallCustomer customer, Long returnId, String idempotencyKey) {
        OrderReturnEntity entity = afterSaleQueryService.requireOwnedReturn(customer, returnId);
        // 已撤销时仍交给正式幂等记录判断：同键重试回放原结果，换键由正式状态机拒绝。
        if (!ScmOrderReturnStatusEnum.PENDING.name().equals(entity.getStatus())
            && !ScmOrderReturnStatusEnum.CANCELLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.AFTERSALE_NOT_CANCELABLE);
        }
        OrderReturnDecisionForm form = new OrderReturnDecisionForm();
        form.setReturnId(returnId);
        form.setVersion(entity.getVersion());
        form.setDecisionReason("客户自行撤销");
        orderReturnService.cancelFromMall(form, idempotencyKey);
        return afterSaleQueryService.detail(customer, returnId);
    }

    private static OrderReturnItemForm item(MallAfterSaleApplyForm.Item item) {
        OrderReturnItemForm result = new OrderReturnItemForm();
        result.setOrderItemId(item.getOrderItemId());
        result.setRequestedQuantity(item.getQuantity());
        return result;
    }
}
