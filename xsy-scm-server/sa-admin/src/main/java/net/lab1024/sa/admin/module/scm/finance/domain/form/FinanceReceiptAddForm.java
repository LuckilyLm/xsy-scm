package net.lab1024.sa.admin.module.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;

import java.time.OffsetDateTime;

/**
 * 收款登记请求（F1-3A，仅 {@code NORMAL} 收款）。
 *
 * <p>刻意只有这六个字段：收款登记不携带应收、核销额、状态、审批人、附件、币种、账期或余额
 * （设计稿 §1 / §16 与第二批 Q15 / Q16 / Q20 / Q21）。核销属 F1-4，附件与账期不属 Finance R1。
 */
@Data
public class FinanceReceiptAddForm {

    @NotNull
    private Long customerId;

    /**
     * 收款金额：一律 JSON 字符串，整数最多 14 位、小数最多 4 位；
     * 服务端按 {@link ScmDecimalStrings} 统一成 scale 4，并要求严格大于 0。
     */
    @NotNull
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    /**
     * 方式取 {@code ScmFinancePaymentMethodEnum} 三值（第二批 Q21：Java enum + DB CHECK，不入字典）。
     * 在线支付 / 余额 / COD / 充值属 P5，本期不出现。
     */
    @NotNull
    private String method;

    /**
     * 实际收款业务时点，由登记人填写。本期不加「不得晚于当前时间」之类的额外规则（设计稿 §5）；
     * 也不接受服务端 {@code now()} 兜底 —— 时点是业务事实，不是落库时刻。
     */
    @NotNull
    private OffsetDateTime receivedAt;

    /**
     * 资金凭据号（银行流水号等），只是文本：允许为空、允许重复，
     * 既不参与幂等也不建唯一约束（第二批 Q25；防重是 Idempotency-Key + 来源唯一索引）。
     */
    @Size(max = 128)
    private String externalReference;

    @Size(max = 500)
    private String remark;
}
