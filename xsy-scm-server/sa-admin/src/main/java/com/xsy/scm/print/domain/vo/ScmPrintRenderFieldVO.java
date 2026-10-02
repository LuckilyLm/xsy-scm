package com.xsy.scm.print.domain.vo;

import lombok.Data;

/**
 * 渲染结果里的一个「标签 : 值」（表头字段或合计字段）。
 *
 * <p>
 * 标签由服务端按单据类型给出，不是模板里存的 —— 模板只挑字段，改不了标签。
 */
@Data
public class ScmPrintRenderFieldVO {

    private String key;

    private String label;

    /**
     * 已格式化的文本值；空值渲染为空串（打印时留白，不伪造 0）。
     */
    private String value;
}
