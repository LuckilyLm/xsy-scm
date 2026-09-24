package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 打印预览：固定版式的小票 / 标签内容，由任务与明细行现算，库里不留副本。
 * 预览本身不改状态、不计次。
 */
@Data
public class SortingPrintVO {
    private Long taskId;
    private String taskNo;
    private String warehouseNameSnapshot;
    private String assigneeName;
    private String status;
    private Integer printCount;
    private OffsetDateTime generatedAt;
    private List<SortingTaskItemVO> items;
}
