package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模板版式方向。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPrintOrientationEnum {

    PORTRAIT("纵向"),

    LANDSCAPE("横向");

    private final String label;

    public static boolean isSupported(String orientation) {
        for (ScmPrintOrientationEnum item : values()) {
            if (item.name().equals(orientation)) {
                return true;
            }
        }
        return false;
    }
}
