package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 秤读数上报（设备事件）。
 *
 * <p>
 * {@code eventKey} 必填：设备在弱网下会重试，用「时间 + 读数」当键会在静止时把多个真实读数
 * 压成一个，用随机键则完全去不了重。
 *
 * <p>
 * {@code taskItemId} 必填：读数必须能落到确定的任务明细上，否则「接受」这一步没有写入目标，
 * 只能靠人去猜是称的哪一行。
 */
@Data
public class SortingScaleReportForm {

    @NotBlank(message = "事件键不能为空")
    @Size(max = 160, message = "事件键不能超过160个字符")
    private String eventKey;

    @NotNull(message = "分拣任务不能为空")
    @Positive(message = "分拣任务 ID 必须大于0")
    private Long taskId;

    @NotNull(message = "分拣明细不能为空")
    @Positive(message = "分拣明细 ID 必须大于0")
    private Long taskItemId;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码不能超过64个字符")
    private String deviceCode;

    @NotNull(message = "读数不能为空")
    @DecimalMin(value = "0", message = "读数不能小于0")
    @Digits(integer = 14, fraction = 4, message = "读数最多14位整数和4位小数")
    private BigDecimal rawReading;

    @NotBlank(message = "单位不能为空")
    @Size(max = 32, message = "单位不能超过32个字符")
    private String unit;

    /**
     * 设备给出的稳定判定；未稳定的读数可以上报（便于观察跳动），但不能被接受。
     */
    @NotNull(message = "稳定判定不能为空")
    private Boolean stableFlag;

    @NotNull(message = "采集时间不能为空")
    private OffsetDateTime capturedAt;
}
