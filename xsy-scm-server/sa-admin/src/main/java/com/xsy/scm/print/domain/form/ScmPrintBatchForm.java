package com.xsy.scm.print.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 20:00 按配送日期批量打印客户分拣单/送货单。
 *
 * <p>
 * 只允许两种客户级单据；一次最多 200 单。显式给了 orderIds 时按 id 打印（用于重印/勾选打印）， 否则按期望配送日期的北京时间自然日取已确认订单，可再按线路和客户收窄。
 */
@Data
public class ScmPrintBatchForm {
    @NotBlank(message = "单据类型不能为空")
    @Size(max = 40, message = "单据类型不能超过40个字符")
    private String documentType;

    @NotNull(message = "配送日期不能为空")
    private LocalDate businessDate;

    private Long routeId;

    @Size(max = 200, message = "单次客户筛选不能超过200个")
    private List<Long> customerIds;

    @Size(max = 200, message = "单次订单筛选不能超过200个")
    private List<Long> orderIds;

    private Long templateId;

    /** 批量幂等前缀；同一次点击重试传同一个值，逐单复用稳定键。 */
    @Size(max = 64, message = "批量键不能超过64个字符")
    private String batchKey;
}
