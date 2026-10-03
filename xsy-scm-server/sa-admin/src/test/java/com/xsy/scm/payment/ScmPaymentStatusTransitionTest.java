package com.xsy.scm.payment;

import com.xsy.scm.payment.constant.ScmPaymentIntentStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentRefundStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付意图与退款的状态转换表。
 *
 * <p>
 * 转换表本身就是契约：Service 只调 {@code canTransition}，不再各写一份 if。 表一旦恒假，所有意图创建都会停在
 * {@code PAYMENT_INTENT_STATE_INVALID}， 因此这里逐格钉住允许与不允许的转换。
 */
class ScmPaymentStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({"CREATED,PENDING,true", "CREATED,FAILED,true", "CREATED,EXPIRED,true", "CREATED,CLOSED,true",
            "PENDING,SUCCEEDED,true", "PENDING,FAILED,true", "PENDING,EXPIRED,true", "PENDING,CLOSED,true",
            "CREATED,SUCCEEDED,false", "PENDING,CREATED,false", "SUCCEEDED,SUCCEEDED,false"})
    void intentTransitionsFollowTheDocumentedTable(String from, String to, boolean allowed) {
        assertThat(ScmPaymentIntentStatusEnum.canTransition(from, to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"CREATED,PROCESSING,true", "CREATED,FAILED,true", "CREATED,CLOSED,true",
            "PROCESSING,SUCCEEDED,true", "PROCESSING,FAILED,true",
            "CREATED,SUCCEEDED,false", "PROCESSING,CREATED,false", "SUCCEEDED,SUCCEEDED,false"})
    void refundTransitionsFollowTheDocumentedTable(String from, String to, boolean allowed) {
        assertThat(ScmPaymentRefundStatusEnum.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void unknownStatusesAreNeverATransitionEndpoint() {
        assertThat(ScmPaymentIntentStatusEnum.canTransition("NOPE", "PENDING")).isFalse();
        assertThat(ScmPaymentIntentStatusEnum.canTransition("CREATED", "NOPE")).isFalse();
        assertThat(ScmPaymentIntentStatusEnum.canTransition(null, "PENDING")).isFalse();
        assertThat(ScmPaymentRefundStatusEnum.canTransition("NOPE", "PROCESSING")).isFalse();
        assertThat(ScmPaymentRefundStatusEnum.canTransition("CREATED", "NOPE")).isFalse();
    }

    /** 终态不可回退：支付是外部事实，本地改状态改不掉客户已经付过的钱。 */
    @Test
    void terminalStatusesNeverTransitionAgain() {
        for (ScmPaymentIntentStatusEnum from : ScmPaymentIntentStatusEnum.values()) {
            if (!from.isTerminal()) {
                continue;
            }
            for (ScmPaymentIntentStatusEnum to : ScmPaymentIntentStatusEnum.values()) {
                assertThat(ScmPaymentIntentStatusEnum.canTransition(from.name(), to.name())).isFalse();
            }
        }
        for (ScmPaymentRefundStatusEnum from : ScmPaymentRefundStatusEnum.values()) {
            if (!from.isTerminal()) {
                continue;
            }
            for (ScmPaymentRefundStatusEnum to : ScmPaymentRefundStatusEnum.values()) {
                assertThat(ScmPaymentRefundStatusEnum.canTransition(from.name(), to.name())).isFalse();
            }
        }
    }
}
