package com.xsy.scm.delivery.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送距离可能来自路网或估算。估算结果必须明确标记，不能当作真实道路里程；同时记录算法版本。
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
