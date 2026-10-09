package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 订单资金来源只认「支付意图已成功」的支付（PG IT）。
 *
 * <p>渠道交易成功、意图仍停在 {@code PENDING} 的是「差异交易」（渠道实收 ≠ 本地应付）：
 * 它只在渠道与对账域等待人工处理，不能进入订单资金 —— 否则它会以「交易已成功」的身份
 * 让签收在资金校验处整笔回滚。
 */
@DisplayName("订单资金来源只认已成功的支付意图（PG IT）")
class ScmFinanceOrderFundingSourcePgIT extends ScmW5PgITBase {

    @Autowired
    private FinanceOrderFundingSourceDao financeOrderFundingSourceDao;

    @Autowired
    private FinanceOrderFundingPolicy financeOrderFundingPolicy;

    @Test
    @DisplayName("渠道实收 ≠ 本地应付的差异交易不参与订单资金")
    void differentialTransactionIsNotOrderFunding() {
        Long orderId = uniqueNumber();
        Long matchedIntentId = intent(orderId, "SUCCEEDED");
        succeededTransaction(matchedIntentId, "100.0000");
        Long differentialIntentId = intent(orderId, "PENDING");
        succeededTransaction(differentialIntentId, "90.0000");

        List<FinanceOrderFundingDto> facts = financeOrderFundingSourceDao.selectOrderFunding(orderId);

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.getIntentId()).isEqualTo(matchedIntentId);
            assertThat(fact.getIntentStatus()).isEqualTo("SUCCEEDED");
        });
        assertThat(facts).extracting(FinanceOrderFundingDto::getIntentId).doesNotContain(differentialIntentId);
    }

    @Test
    @DisplayName("只剩差异交易时资金校验不再失败，签收因此不被阻断")
    void differentialTransactionDoesNotBreakFundingValidation() {
        Long orderId = uniqueNumber();
        succeededTransaction(intent(orderId, "PENDING"), "90.0000");

        assertThatCode(() -> financeOrderFundingPolicy.requireCompleteOrderFunding(orderId))
                .as("差异交易留在渠道与对账域，不再触发 ORDER_FUNDING_INVALID")
                .doesNotThrowAnyException();
    }

    /** 一条支付意图：本地应付 100。差异交易与正常交易的差别只在意图状态。 */
    private Long intent(Long orderId, String status) {
        return jdbc.queryForObject("INSERT INTO payment_intent (intent_no, customer_id, customer_name_snapshot,"
                        + " source_type, source_id, source_no_snapshot, amount, method, provider, status,"
                        + " succeeded_at, created_by, updated_by)"
                        + " VALUES (?, 1, '资金查询测试客户', 'SALES_ORDER', ?, ?, 100.0000, 'ONLINE', 'MOCK', ?,"
                        + " CASE WHEN ? = 'SUCCEEDED' THEN now() END, '1:1', '1:1') RETURNING id",
                Long.class, "PI-FUND-" + prefix + "-" + UUID.randomUUID(), orderId, "SO-" + orderId, status, status);
    }

    /** 一条渠道已成功的交易：本地应付保持 100，渠道实收单独记，不覆盖本地金额。 */
    private void succeededTransaction(Long intentId, String providerAmount) {
        jdbc.update("INSERT INTO payment_transaction (transaction_no, intent_id, provider,"
                        + " provider_transaction_no, amount, provider_amount, status, paid_at, created_by, updated_by)"
                        + " VALUES (?, ?, 'MOCK', ?, 100.0000, ?, 'SUCCEEDED', now(), '1:1', '1:1')",
                "PT-FUND-" + prefix + "-" + UUID.randomUUID(), intentId, "WX-" + UUID.randomUUID(),
                new BigDecimal(providerAmount));
    }

    /** 与同目录 IT 相同的口径：订单主键由用例独占，避免共享开发库里别的订单把断言撑破。 */
    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }
}
