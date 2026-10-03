package com.xsy.scm.delivery.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.DeliveryErrorCode;
import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanLegVO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 建议输入的版本约束与历史明细还原；回看只读快照，不读当前停靠点。 */
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
        snapshot.put("constraints", Map.of("applied", List.of("SAME_CRS", "START_FIXED"),
                "notApplied", List.of("TIME_WINDOW", "VEHICLE_CAPACITY", "TRAFFIC")));
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
            if (!sameValue(frozen.get(key), current.get(key))) stale();
        }
        if (!(frozen.get("start") instanceof Map<?, ?> start)
                || !(current.get("start") instanceof Map<?, ?> currentStart)) {
            stale();
            return;
        }
        for (String key : List.of("longitude", "latitude", "geomCrs")) {
            if (!sameValue(start.get(key), currentStart.get(key))) stale();
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
                if (!sameValue(oldStop.get(key), currentStop.get(key))) stale();
            }
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
