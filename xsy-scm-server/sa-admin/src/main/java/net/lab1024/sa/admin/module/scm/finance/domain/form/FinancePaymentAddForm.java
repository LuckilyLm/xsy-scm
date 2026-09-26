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
 * 付款登记请求（F1-3B，仅 {@code NORMAL} 付款）。
 *
 * <p>只有两种合法组合（{@code counterpartyType} × {@code sourceType}），服务端逐条判定，
 * 其余组合一律 41139：
 * <ul>
 *   <li>{@code SUPPLIER} + 来源两列全空 —— 供应商付款 / 预付，允许当前没有任何应付（Q16）；</li>
 *   <li>{@code CUSTOMER} + {@code ORDER_REFUND} + {@code sourceId} —— 客户退款付款，
 *       必须指向一张已 {@code COMPLETED} 的退款且金额逐值相等（Q19）。</li>
 * </ul>
 *
 * <p>刻意不含应付、核销额、状态、审批人、账户、币种、附件、余额（设计稿 §1 / §6）。
 * 付款与应付的对应关系只能由 {@code finance_write_off}（F1-4）表达，这里不接受 {@code payableId}。
 */
@Data
public class FinancePaymentAddForm {

    /**
     * {@code ScmFinanceCounterpartyTypeEnum} 两值之一。
     */
    @NotNull
    private String counterpartyType;

    /**
     * 对方主键：{@code SUPPLIER} 时为 {@code supplier.id}；
     * {@code CUSTOMER} 时<b>以 {@code order_refund.customer_id} 为权威</b>，本字段只用于一致性校验。
     */
    @NotNull
    private Long counterpartyId;

    /**
     * 付款金额：一律 JSON 字符串，整数最多 14 位、小数最多 4 位；
     * 服务端按 {@link ScmDecimalStrings} 统一成 scale 4，并要求严格大于 0。
     */
    @NotNull
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    /**
     * 方式取 {@code ScmFinancePaymentMethodEnum} 三值，与收款共用同一套枚举（Q21）。
     */
    @NotNull
    private String method;

    /**
     * 实际付款业务时点，由登记人填写。服务端不用 {@code now()} 兜底，
     * 也不加「不得晚于当前时间」的规则（同收款，设计稿 §6）。
     */
    @NotNull
    private OffsetDateTime paidAt;

    /**
     * 资金凭据号，只是文本：可空、可重复、不建唯一约束（Q25）。
     *
     * <p><b>不会从 {@code order_refund.external_reference} 自动带入</b>：那一列属于订单域的退款事实，
     * 本列属于财务的真实资金动作，两者职责不同，必须由登记人独立提供。
     */
    @Size(max = 128)
    private String externalReference;

    /**
     * 仅 {@code CUSTOMER} 侧使用，且本期只有 {@code ORDER_REFUND}；
     * {@code SUPPLIER} 侧必须为空（与 {@link #sourceId} 成对，{@code ck_finance_payment_source_pairing}）。
     */
    private String sourceType;

    /**
     * {@code order_refund.id}；与 {@link #sourceType} 同空同非空。
     */
    private Long sourceId;

    @Size(max = 500)
    private String remark;
}
