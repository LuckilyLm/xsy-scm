package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品集合完成：把该明细当前全部待完成筐作为一个整体确认。
 *
 * <p>
 * 不接收累计总量：服务端按筐重算，设备提交的合计不参与判定。 {@code manifestHash} 用来证明「设备确认的筐集合」与「服务端读到的筐集合」是同一批， 避免完成动作漏掉并发新增的一筐。
 */
@Data
public class SortingScaleCompleteForm {

    @NotBlank(message = "操作编号不能为空")
    @Size(max = 160, message = "操作编号最多 160 个字符")
    private String operationId;

    @NotNull(message = "分拣任务不能为空")
    private Long taskId;

    @NotNull(message = "分拣明细不能为空")
    private Long taskItemId;

    /**
     * 采集时冻结的作业版本；与筐记录不一致说明任务已改派或重开。
     */
    @NotBlank(message = "作业版本不能为空")
    @Size(max = 40, message = "作业版本最多 40 个字符")
    private String jobVersion;

    @NotNull(message = "分拣明细版本不能为空")
    private Integer taskItemVersion;

    @NotBlank(message = "集合摘要不能为空")
    @Pattern(regexp = "[0-9A-Fa-f]{64}", message = "集合摘要必须是 64 位十六进制")
    private String manifestHash;

    @NotBlank(message = "分拣结果不能为空")
    @Pattern(regexp = "NORMAL|SHORT|OUT_OF_STOCK|OVER", message = "分拣结果无效")
    private String result;

    @Size(max = 500, message = "差异原因不能超过500个字符")
    private String reason;

    /**
     * 以下三项由设备接入层按已校验的设备与员工身份填入，不接受请求体自报。
     */
    private Long deviceId;

    private Long deviceSessionId;

    private Long employeeId;
}
