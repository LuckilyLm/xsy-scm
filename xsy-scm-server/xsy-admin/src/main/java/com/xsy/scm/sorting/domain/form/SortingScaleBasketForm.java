package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 一筐秤读数。与单事件上报分开：这里带筐号、净重与换算依据，但同样不写分拣结果。
 *
 * <p>
 * 净重与业务单位成对出现：只有净重没有单位，或单位不明，都无法参与求和，服务端直接拒绝，不按公斤兜底。
 */
@Data
public class SortingScaleBasketForm {

    @NotBlank(message = "事件键不能为空")
    @Size(max = 160, message = "事件键最多 160 个字符")
    private String eventKey;

    @NotBlank(message = "操作编号不能为空")
    @Size(max = 160, message = "操作编号最多 160 个字符")
    private String operationId;

    @NotNull(message = "分拣任务不能为空")
    private Long taskId;

    @NotNull(message = "分拣明细不能为空")
    private Long taskItemId;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码最多 64 个字符")
    private String deviceCode;

    /** 设备授权会话；由设备接入层解析后写入，不接受请求体自报。 */
    private Long deviceSessionId;

    /** 明细内递增的筐号，从 1 开始。 */
    @NotNull(message = "筐号不能为空")
    @Min(value = 1, message = "筐号必须从 1 开始")
    private Integer basketNo;

    /** 设备原始读数；负值属于无效采集，服务端拒绝。 */
    @NotNull(message = "读数不能为空")
    @DecimalMin(value = "0", message = "读数不能小于 0")
    @Digits(integer = 14, fraction = 4, message = "读数最多14位整数和4位小数")
    private BigDecimal rawReading;

    @NotBlank(message = "原始单位不能为空")
    @Size(max = 32, message = "原始单位最多 32 个字符")
    private String rawUnit;

    @NotNull(message = "净重不能为空")
    @DecimalMin(value = "0", message = "净重不能小于 0")
    @Digits(integer = 14, fraction = 4, message = "净重最多14位整数和4位小数")
    private BigDecimal netWeight;

    @NotBlank(message = "业务单位不能为空")
    @Size(max = 32, message = "业务单位最多 32 个字符")
    private String businessUnit;

    /** {@code NET} 或 {@code GROSS}；毛重不得在未去皮时直接入库。 */
    @NotBlank(message = "重量类型不能为空")
    @Pattern(regexp = "NET|GROSS", message = "重量类型只能是 NET 或 GROSS")
    private String weightKind;

    @Size(max = 32, message = "换算规则最多 32 个字符")
    private String conversionRule;

    @DecimalMin(value = "0", message = "皮重不能小于 0")
    @Digits(integer = 14, fraction = 4, message = "皮重最多14位整数和4位小数")
    private BigDecimal tareWeight;

    @NotNull(message = "稳定判定不能为空")
    private Boolean stableFlag;

    @Size(max = 32, message = "稳定来源最多 32 个字符")
    private String stabilitySource;

    @Size(max = 40, message = "作业版本最多 40 个字符")
    private String jobVersion;

    private Integer taskItemVersion;

    @NotNull(message = "采集时间不能为空")
    private OffsetDateTime capturedAt;

    @Size(max = 40, message = "协议类型最多 40 个字符")
    private String protocolType;

    @Size(max = 40, message = "设备程序版本最多 40 个字符")
    private String agentVersion;
}
