package com.xsy.scm.print.domain.vo;

import lombok.Data;

/**
 * 渲染结果里的一列明细。
 */
@Data
public class ScmPrintRenderColumnVO {

    private String key;

    private String label;

    /**
     * 是否右对齐（数量与金额）。
     */
    private boolean numeric;
}
