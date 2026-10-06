package com.xsy.scm.delivery.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 距离矩阵 provider 契约（ADR-008：距离矩阵通过可替换 provider 提供）。
 *
 * <p>
 * <b>估算实现必须自报家门</b>：{@link #estimated()} 为 {@code true} 的实现（直线距离、测试桩等） 会被原样写进排线建议，界面上必须显示「估算，不是真实路网」。把估算结果当路网结果展示，
 * 会让人按一个不存在的行驶距离去安排发车时间。
 *
 * <p>
 * {@link #providerCode()} 与 {@link #providerVersion()} 一起冻结进建议：换 provider 或升级算法后，
 * 历史建议仍能说明「当初是按哪一版算出来的」，而不是看起来像同一套算法的另一个结果。
 */
public interface DeliveryDistanceMatrixProvider {

    /**
     * 稳定标识，例如 {@code STRAIGHT_LINE_HAVERSINE}。
     */
    String providerCode();

    /**
     * 算法版本；同一 provider 改变算法时必须升版本，历史建议因此可区分。
     */
    String providerVersion();

    /**
     * 是否为估算（非真实路网 / 非实时交通）。
     */
    boolean estimated();

    /**
     * 计算对称距离矩阵，单位<b>米</b>，{@code matrix[i][j]} 为点 i 到点 j 的距离。
     *
     * <p>
     * 点序必须与入参一致；对角线为 0。所有点必须同一坐标系，混用坐标系时实现应直接拒绝 —— 不同 CRS 的经纬度相减得到的距离在几百米量级上是错的。
     */
    BigDecimal[][] matrix(List<GeoPoint> points);
}
