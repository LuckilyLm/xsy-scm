package com.xsy.scm.sorting.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 商品集合完成结果。
 *
 * <p>
 * {@code totalQuantity} 是服务端按筐重算的累计净重，{@code sortedQuantity} 是写入任务明细的 正式分拣量；两者应相等，出现差异说明中间有并发修改，需要人工核查。
 */
@Data
public class SortingScaleCompletionVO {

    private Long completionId;

    private String operationId;

    private Long taskItemId;

    private Integer basketCount;

    private BigDecimal totalQuantity;

    private BigDecimal sortedQuantity;

    private String businessUnit;

    private String result;

    private String status;

    /** 同一操作号重复提交时为 {@code true}。 */
    private Boolean duplicated;
}
