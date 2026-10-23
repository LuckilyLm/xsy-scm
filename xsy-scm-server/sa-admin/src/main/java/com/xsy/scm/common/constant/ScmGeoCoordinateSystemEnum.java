package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 位置记录使用的坐标参考系。
 *
 * <p>
 * 两套坐标不能混用：把 GCJ02 的点直接画在 WGS84 底图上会偏移几百米。{@code desc} 供页面与导出使用，数据库仍存枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmGeoCoordinateSystemEnum {

    /** 国测局坐标，国内地图底图常用。 */
    GCJ02("国测局坐标"),

    /** 国际通用坐标，GPS 原始输出。 */
    WGS84("国际坐标");

    private final String desc;
}
