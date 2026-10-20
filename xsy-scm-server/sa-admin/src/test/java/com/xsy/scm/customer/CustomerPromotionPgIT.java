package com.xsy.scm.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;
import com.xsy.scm.customer.service.CustomerPromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 客户推广二维码：码的签发与失效、扫码只追加。
 *
 * <p>
 * 码是公开入口，因此重点验证「什么时候不该再能用」：重新生成让旧码失效、撤销后立即不可用。
 */
class CustomerPromotionPgIT extends ScmW5PgITBase {

    @Autowired
    private CustomerPromotionService customerPromotionService;

    @Test
    @DisplayName("#1 生成推广码后扫码记录来源，重复生成让旧码失效（40041）")
    void issueCodeRecordsScanAndReissueRevokes() {
        Long customerId = newCustomer();
        CustomerPromotionVO first = customerPromotionService.create(customerId, "W9 IT");
        assertThat(first.getCode()).hasSize(10);

        CustomerPromotionVO.Landing landing = customerPromotionService.landing(first.getCode(), "127.0.0.1",
                "W9 IT agent");
        assertThat(landing.getCustomerName()).isNotBlank();

        // 扫码只追加：一条记录，且能查到
        assertThat(customerPromotionService.scans(first.getId(), 20)).hasSize(1);

        CustomerPromotionVO second = customerPromotionService.create(customerId, "W9 IT");
        assertThatThrownBy(() -> customerPromotionService.landing(first.getCode(), "127.0.0.1", null))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(40041));
        assertThat(customerPromotionService.landing(second.getCode(), "127.0.0.1", null).getCustomerName())
                .isNotBlank();
    }

    @Test
    @DisplayName("#2 撤销后不可扫码，未知码被拒（40042）")
    void revokeBlocksLanding() {
        Long customerId = newCustomer();
        CustomerPromotionVO code = customerPromotionService.create(customerId, "W9 IT");
        customerPromotionService.revoke(code.getId());

        assertThatThrownBy(() -> customerPromotionService.landing(code.getCode(), "127.0.0.1", null))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(40041));

        assertThatThrownBy(() -> customerPromotionService.landing("NOTEXIST99", "127.0.0.1", null))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(40042));
    }
}
