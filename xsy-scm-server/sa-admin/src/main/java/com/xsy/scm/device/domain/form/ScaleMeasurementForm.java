package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 一筐秤读数上报，对应秤端 {@code POST /measurements}。
 *
 * <p>
 * 数量与重量都是字符串：设备原始读数可能带尾随零，用字符串可以原样保留，避免经过浮点。
 *
 * <p>
 * 设备编码与员工身份不从这里取：设备编码来自已校验的设备凭证，员工来自授权会话。 请求体里即便带了这两个字段也不会被采用。
 */
@Data
public class ScaleMeasurementForm {

    @Size(max = 16, message = "协议版本最多 16 个字符")
    private String protocolVersion;

    @NotBlank(message = "事件键不能为空")
    @Size(max = 160, message = "事件键最多 160 个字符")
    private String eventKey;

    /**
     * 一次人工操作的幂等号；重试必须复用，不能重新生成。
     */
    @NotBlank(message = "操作编号不能为空")
    @Size(max = 160, message = "操作编号最多 160 个字符")
    private String operationId;

    /**
     * 明细内递增的筐号，从 1 开始。
     */
    @NotNull(message = "筐号不能为空")
    @Min(value = 1, message = "筐号必须从 1 开始")
    private Integer basketNo;

    @NotNull(message = "分拣任务不能为空")
    private Long taskId;

    @NotNull(message = "分拣明细不能为空")
    private Long taskItemId;

    private Long taskItemVersion;

    @Size(max = 160, message = "作业标识最多 160 个字符")
    private String jobId;

    @Size(max = 40, message = "作业版本最多 40 个字符")
    private String jobVersion;

    @NotNull(message = "采集时间不能为空")
    private OffsetDateTime capturedAt;

    /**
     * 设备原始读数，非负。
     */
    @NotBlank(message = "读数不能为空")
    @Size(max = 40, message = "读数最多 40 个字符")
    private String rawValue;

    @NotBlank(message = "原始单位不能为空")
    @Size(max = 32, message = "原始单位最多 32 个字符")
    private String rawUnit;

    /**
     * 换算后的净重；毛重在未去皮时不允许上报为可计入的读数。
     */
    @NotBlank(message = "净重不能为空")
    @Size(max = 40, message = "净重最多 40 个字符")
    private String netWeight;

    @NotBlank(message = "业务单位不能为空")
    @Size(max = 32, message = "业务单位最多 32 个字符")
    private String businessUnit;

    /**
     * {@code NET} 或 {@code GROSS}；缺省按 NET 处理但服务端仍会复核。
     */
    @Size(max = 8, message = "重量类型最多 8 个字符")
    private String weightKind;

    @NotNull(message = "稳定判定不能为空")
    private Boolean stableFlag;

    @Size(max = 32, message = "稳定来源最多 32 个字符")
    private String stabilitySource;

    @Size(max = 32, message = "换算规则最多 32 个字符")
    private String conversionRule;

    @Size(max = 40, message = "皮重最多 40 个字符")
    private String tareWeight;

    @Size(max = 40, message = "采样序号最多 40 个字符")
    private String readingSequence;

    @Size(max = 40, message = "协议类型最多 40 个字符")
    private String protocolType;

    @Size(max = 40, message = "设备程序版本最多 40 个字符")
    private String agentVersion;
}
