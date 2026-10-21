package com.xsy.scm.device.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 商品集合完成结果。
 *
 * <p>
 * {@code totalQuantity} 是服务端按筐重算的净重，{@code sortedQuantity} 是写入任务明细的正式分拣量。
 */
@Data
public class ScaleItemCompletionVO {

    private Long completionId;

    private String operationId;

    private String taskItemId;

    private Integer basketCount;

    private BigDecimal totalQuantity;

    private BigDecimal sortedQuantity;

    private String businessUnit;

    private String result;

    private String state;

    private Boolean duplicated;
}
