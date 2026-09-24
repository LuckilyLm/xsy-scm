package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 任务详情：头部 + 全部未删除明细行（含已随取消释放的历史行，取消任务仍需可查）。
 */
@Data
public class SortingTaskDetailVO {
    private SortingTaskVO task;
    private List<SortingTaskItemVO> items;
}
