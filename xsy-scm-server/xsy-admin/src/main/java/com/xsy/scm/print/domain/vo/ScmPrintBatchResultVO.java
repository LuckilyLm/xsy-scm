package com.xsy.scm.print.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量预览/打印结果。每单独立成功或失败，一张失败不抹掉其他单的结果。
 */
@Data
public class ScmPrintBatchResultVO {
    private int total;
    private int succeeded;
    private int failed;
    private List<ScmPrintBatchItemVO> items = new ArrayList<>();
    /** 成功的版面；失败的单据没有版面。 */
    private List<ScmPrintRenderVO> renders = new ArrayList<>();
}
