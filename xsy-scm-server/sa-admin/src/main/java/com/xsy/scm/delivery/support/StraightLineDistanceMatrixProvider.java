package com.xsy.scm.delivery.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.DeliveryErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 直线（大圆）距离矩阵：<b>估算</b>实现，不查路网、不看实时交通。
 *
 * <p>
 * 存在的意义是让排线建议在没有商用路网服务时也能跑起来，并且把「这是估算」写进建议快照。 它刻意不叫「距离服务」或「路网 provider」：名字一旦暗示真实路网，后面所有基于它的 排线结论都会被当成路网结论使用。
 *
 * <p>
 * 用 Haversine 而不是平面近似：跨城市线路的纬度差会带来百分之几的系统性偏差， 而 Haversine 的成本与平面近似同量级。
 */
@Component
public class StraightLineDistanceMatrixProvider implements DeliveryDistanceMatrixProvider {

    private static final String CODE = "STRAIGHT_LINE_HAVERSINE";

    private static final String VERSION = "1";

    private static final double EARTH_RADIUS_METERS = 6_371_008.8d;

    /** 距离保留 4 位小数（米），与全仓数量金额的定点口径一致。 */
    private static final int SCALE = 4;

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public String providerVersion() {
        return VERSION;
    }

    @Override
    public boolean estimated() {
        return true;
    }

    @Override
    public BigDecimal[][] matrix(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
        }
        String crs = null;
        for (GeoPoint point : points) {
            if (point == null || point.longitude() == null || point.latitude() == null) {
                throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
            }
            if (point.geomCrs() == null || point.geomCrs().isBlank()) {
                throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
            }
            if (crs == null) {
                crs = point.geomCrs();
            } else if (!crs.equals(point.geomCrs())) {
                // 混用坐标系算出来的距离在几百米量级上就是错的，且不会报错，只能在这里挡
                throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
            }
        }
        int size = points.size();
        BigDecimal[][] matrix = new BigDecimal[size][size];
        for (int i = 0; i < size; i++) {
            matrix[i][i] = BigDecimal.ZERO.setScale(SCALE);
            for (int j = i + 1; j < size; j++) {
                BigDecimal distance = meters(points.get(i), points.get(j));
                matrix[i][j] = distance;
                matrix[j][i] = distance;
            }
        }
        return matrix;
    }

    private static BigDecimal meters(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude().doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(EARTH_RADIUS_METERS * c).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
