package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 正式生成打印的登记结果：只有计次、时间与操作人，没有任何状态或库存变化。
 */
@Data
public class SortingPrintResultVO {
    private Long taskId;
    private String taskNo;
    private Integer itemCount;
    private Integer printCount;
    private OffsetDateTime generatedAt;
}
