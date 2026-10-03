package com.xsy.scm.balance.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 人工更正余额。
 *
 * <p>
 * <b>没有「编辑余额」接口</b>：更正也是追加一条流水，历史不可改。因此这个表单要求
 * 方向、金额、原因三样齐全，且调用方必须带 {@code Idempotency-Key} —— 动的是客户的钱，
 * 重复提交的代价是真金白银。
 */
@Data
public class BalanceCorrectionForm {

    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    /** {@code CREDIT}（增加）/ {@code DEBIT}（减少）。 */
    @NotBlank(message = "方向不能为空")
    private String direction;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "金额必须大于0")
    @Digits(integer = 14, fraction = 4, message = "金额最多14位整数和4位小数")
    private BigDecimal amount;

    /** 必填：动客户的钱没有理由不可接受（库上也有同义 CHECK）。 */
    @NotBlank(message = "更正原因不能为空")
    @Size(max = 500, message = "更正原因最多500字")
    private String reason;
}
