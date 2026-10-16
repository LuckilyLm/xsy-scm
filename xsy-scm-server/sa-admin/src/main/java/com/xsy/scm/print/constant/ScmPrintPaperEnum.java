package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 打印模板仅支持 A4 单据和 80mm 热敏小票。具体缩放由浏览器和打印机处理，不允许在模板中自定义任意纸张尺寸。 */
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
