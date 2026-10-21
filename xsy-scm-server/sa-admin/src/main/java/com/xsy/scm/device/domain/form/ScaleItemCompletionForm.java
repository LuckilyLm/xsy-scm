package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品集合完成，对应秤端 {@code POST /items/{taskItemId}/complete}。
 *
 * <p>
 * 不接收累计总量：服务端按筐重算。{@code manifestHash} 只证明设备与服务的筐集合一致。
 */
@Data
public class ScaleItemCompletionForm {

    @NotBlank(message = "操作编号不能为空")
    @Size(max = 160, message = "操作编号最多 160 个字符")
    private String operationId;

    @Size(max = 160, message = "作业标识最多 160 个字符")
    private String jobId;

    @Size(max = 40, message = "作业版本最多 40 个字符")
    private String jobVersion;

    @NotBlank(message = "分拣任务不能为空")
    @Size(max = 40, message = "分拣任务标识最多 40 个字符")
    private String taskId;

    private Long taskItemVersion;

    @NotBlank(message = "集合摘要不能为空")
    @Pattern(regexp = "[0-9A-Fa-f]{64}", message = "集合摘要必须是 64 位十六进制")
    private String manifestHash;

    @NotBlank(message = "分拣结果不能为空")
    @Pattern(regexp = "NORMAL|SHORT|OUT_OF_STOCK|OVER", message = "分拣结果无效")
    private String result;

    @Size(max = 500, message = "差异原因不能超过500个字符")
    private String reason;
}
