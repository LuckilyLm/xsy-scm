package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模板可选纸张。取值刻意只有两种：
 *
 * <ul>
 * <li>{@link #A4}：单据类（采购单、发货单），配浏览器 {@code @page} 的毫米边距；</li>
 * <li>{@link #TICKET_80}：热敏小票，固定 80mm 宽、无横向。</li>
 * </ul>
 *
 * <p>
 * 不做自定义毫米尺寸：纸张尺寸最终由打印机驱动与浏览器缩放决定（见 ADR-007 的外部验收边界），
 * 在模板里放开任意尺寸只会造出「配置里是 210×297，出来是别的」这种无法复现的问题。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPrintPaperEnum {

    A4("A4 纸", true),

    TICKET_80("80mm 小票", false);

    private final String label;

    /**
     * 是否允许横向。小票是定宽卷纸，没有横向这一说。
     */
    private final boolean landscapeAllowed;

    public static boolean isSupported(String paper) {
        for (ScmPrintPaperEnum item : values()) {
            if (item.name().equals(paper)) {
                return true;
            }
        }
        return false;
    }
}
