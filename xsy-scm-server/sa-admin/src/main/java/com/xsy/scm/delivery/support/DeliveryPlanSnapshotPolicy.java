package com.xsy.scm.delivery.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.DeliveryErrorCode;
import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanLegVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanProposalVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 建议输入的版本约束和历史明细还原；回看只读当时保存的数据，不读当前停靠点。
 */
public final class DeliveryPlanSnapshotPolicy {
    private DeliveryPlanSnapshotPolicy() {
    }

    public static Map<String, Object> capture(DeliveryRouteEntity route, List<DeliveryRouteStopEntity> stops) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("routeVersion", route.getVersion());
        snapshot.put("warehouseId", route.getWarehouseId());
        snapshot.put("driverId", route.getDriverId());
        snapshot.put("vehicleId", route.getVehicleId());
        snapshot.put("deliveryDate", String.valueOf(route.getDeliveryDate()));
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("longitude", plain(route.getStartLongitude()));
        start.put("latitude", plain(route.getStartLatitude()));
        start.put("geomCrs", route.getStartGeomCrs());
        snapshot.put("start", start);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DeliveryRouteStopEntity stop : stops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stopId", stop.getId());
            item.put("version", stop.getVersion());
            item.put("stopSeq", stop.getStopSeq());
            item.put("customerNameSnapshot", stop.getCustomerNameSnapshot());
            item.put("addressSnapshot", stop.getAddressSnapshot());
            item.put("longitude", plain(stop.getLongitude()));
            item.put("latitude", plain(stop.getLatitude()));
            item.put("geomCrs", stop.getGeomCrs());
            rows.add(item);
        }
        snapshot.put("stops", rows);
        snapshot.put("constraints", Map.of("applied", List.of("SAME_CRS", "START_FIXED"), "notApplied",
            List.of("TIME_WINDOW", "VEHICLE_CAPACITY", "TRAFFIC")));
        return snapshot;
    }

    public static void requireCurrent(DeliveryPlanProposalEntity proposal, DeliveryRouteEntity route,
        List<DeliveryRouteStopEntity> stops) {
        Map<String, Object> frozen = proposal.getInputSnapshot();
        // 旧建议没有生成时版本，无法证明仍适用；仍可回看，但必须重新生成后应用。
        if (frozen == null || frozen.get("routeVersion") == null) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STALE);
        }
        Map<String, Object> current = capture(route, stops);
        for (String key : List.of("routeVersion", "warehouseId", "driverId", "vehicleId", "deliveryDate")) {
            if (!sameValue(frozen.get(key), current.get(key)))
                stale();
        }
        if (!(frozen.get("start") instanceof Map<?, ?> start)
            || !(current.get("start") instanceof Map<?, ?> currentStart)) {
            stale();
            return;
        }
        for (String key : List.of("longitude", "latitude", "geomCrs")) {
            if (!sameValue(start.get(key), currentStart.get(key)))
                stale();
        }
        if (!(frozen.get("stops") instanceof List<?> oldStops) || oldStops.size() != stops.size()) {
            stale();
            return;
        }
        List<?> currentStops = (List<?>) current.get("stops");
        for (int i = 0; i < oldStops.size(); i++) {
            if (!(oldStops.get(i) instanceof Map<?, ?> oldStop)) {
                stale();
                return;
            }
            Map<?, ?> currentStop = (Map<?, ?>) currentStops.get(i);
            for (String key : List.of("stopId", "version", "stopSeq", "customerNameSnapshot", "addressSnapshot",
                "longitude", "latitude", "geomCrs")) {
                if (!sameValue(oldStop.get(key), currentStop.get(key)))
                    stale();
            }
        }
    }

    /** 只从生成时保存的输入还原地图起点、旧顺序和约束说明；缺字段时安全降级。 */
    public static void fillPreviewSource(DeliveryPlanProposalEntity proposal, DeliveryPlanProposalVO view) {
        Map<String, Object> frozen = proposal.getInputSnapshot();
        if (frozen == null)
            return;
        Object version = frozen.get("routeVersion");
        if (version != null) {
            try {
                view.setSourceRouteVersion(Integer.valueOf(String.valueOf(version)));
            } catch (NumberFormatException ignored) {
                // 历史坏数据只禁用页面快速应用，不放宽服务端的 requireCurrent。
            }
        }
        if (frozen.get("start") instanceof Map<?, ?> start) {
            try {
                view.setSourceLongitude(decimal(start.get("longitude")));
                view.setSourceLatitude(decimal(start.get("latitude")));
                if (start.get("geomCrs") != null)
                    view.setSourceGeomCrs(String.valueOf(start.get("geomCrs")));
            } catch (NumberFormatException ignored) {
                // 不使用当前路线的坐标冒充历史起点。
            }
        }
        if (frozen.get("constraints") instanceof Map<?, ?> constraints
            && constraints.get("notApplied") instanceof List<?> excluded) {
            view.setIgnoredFactors(excluded.stream().map(String::valueOf).toList());
        }
    }

    public static List<DeliveryPlanLegVO> legs(DeliveryPlanProposalEntity proposal) {
        try {
            Map<String, Map<?, ?>> inputs = new LinkedHashMap<>();
            List<?> stops = (List<?>) proposal.getInputSnapshot().get("stops");
            for (Object value : stops) {
                Map<?, ?> stop = (Map<?, ?>) value;
                inputs.put(String.valueOf(stop.get("stopId")), stop);
            }
            List<?> result = (List<?>) proposal.getResultSnapshot().get("legs");
            List<DeliveryPlanLegVO> legs = new ArrayList<>();
            for (Object value : result) {
                Map<?, ?> item = (Map<?, ?>) value;
                Map<?, ?> input = Objects.requireNonNull(inputs.get(String.valueOf(item.get("stopId"))));
                DeliveryPlanLegVO leg = new DeliveryPlanLegVO();
                leg.setSeq(Integer.valueOf(String.valueOf(item.get("seq"))));
                if (input.get("stopSeq") != null)
                    leg.setOriginalSeq(Integer.valueOf(String.valueOf(input.get("stopSeq"))));
                leg.setStopId(Long.valueOf(String.valueOf(item.get("stopId"))));
                leg.setCustomerNameSnapshot((String) input.get("customerNameSnapshot"));
                leg.setAddressSnapshot((String) input.get("addressSnapshot"));
                leg.setLongitude(decimal(input.get("longitude")));
                leg.setLatitude(decimal(input.get("latitude")));
                leg.setLegDistance(decimal(item.get("legDistance")));
                leg.setCumulativeDistance(decimal(item.get("cumulativeDistance")));
                legs.add(leg);
            }
            return legs;
        } catch (IllegalArgumentException | ClassCastException | NullPointerException exception) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }
    }

    private static boolean sameValue(Object left, Object right) {
        return Objects.equals(left == null ? null : String.valueOf(left), right == null ? null : String.valueOf(right));
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private static void stale() {
        throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STALE);
    }
}
