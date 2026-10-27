package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import org.springframework.stereotype.Service;

/** 兼容旧调用点，但商城渠道不再授权任何扣款；后台资金命令仍由原支付域处理。 */
@Service
public class MallPaymentService {
    public MallOrderVO pay(RequestMallCustomer customer, Long orderId, String idempotencyKey) {
        throw new ScmBusinessException(MallErrorCode.ORDER_PAYMENT_DISABLED);
    }
}
