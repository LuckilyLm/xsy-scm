package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.service.MallPaymentService;
import org.junit.jupiter.api.Test;

class MallPaymentServiceTest {
    @Test
    void oldClientCannotPayEvenWithAnOrderAndIdempotencyKey() {
        MallPaymentService service = new MallPaymentService();
        assertThatThrownBy(() -> service.pay(new RequestMallCustomer(), 21L, "legacy-pay"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.ORDER_PAYMENT_DISABLED));
    }
}
