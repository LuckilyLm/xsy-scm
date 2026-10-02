package com.xsy.scm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.DeliveryErrorCode;
import com.xsy.scm.delivery.constant.ScmDeliveryRouteStatusEnum;
import com.xsy.scm.delivery.dao.DeliveryPlanProposalDao;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.delivery.dao.DeliveryRouteDao;
import com.xsy.scm.delivery.dao.DeliveryRouteStopDao;
import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRecord;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.form.DeliveryPlanApplyForm;
import com.xsy.scm.delivery.domain.form.DeliveryPlanDiscardForm;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanLegVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanProposalVO;
import com.xsy.scm.delivery.support.DeliveryDistanceMatrixProvider;
import com.xsy.scm.delivery.support.GeoPoint;
import com.xsy.scm.delivery.support.NearestNeighbourPlanner;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 辅助排线建议：生成 → 比较 → 显式应用 / 放弃。
 *
 * <p>
 * 三条不变量：
 * <ul>
 * <li><b>只建议，不动线路</b>：生成建议不改任何停靠顺序、不改线路状态；只有 {@code apply}
 * 写回顺序，且要求线路仍是 {@code DRAFT}（未发车）。建议<b>不会</b>自动发车，也不会覆盖
 * 已规划 / 已发车的线路。</li>
 * <li><b>输入与结果一起冻结</b>：停靠点、起点、坐标系、生效与未生效的约束、provider 版本、
 * 每段与累计距离全部写进快照。表上有触发器拒绝改动，因此「建议里看到的距离」与
 * 「应用时依据的距离」必然是同一份。</li>
 * <li><b>一条线路同时至多一条待确认建议</b>：生成新建议时旧的待确认建议转为放弃，
 * 避免「应用哪一个」变成猜。</li>
 * </ul>
 *
 * <p>
 * 本类不写库存、不写订单、不发车：应用建议只等价于一次「按给定顺序重排停靠点」，
 * 与手工拖拽排序走同一套写入纪律。
 */
@Service
@RequiredArgsConstructor
public class DeliveryPlanProposalService {

    private static final int HISTORY_LIMIT = 20;

    private final DeliveryPlanProposalDao deliveryPlanProposalDao;

    private final DeliveryRouteDao deliveryRouteDao;

    private final DeliveryRouteStopDao deliveryRouteStopDao;

    private final DeliveryQueryDao deliveryQueryDao;

    private final DeliveryDistanceMatrixProvider distanceMatrixProvider;

    /**
     * 生成建议（只读业务数据 + 追加一条建议记录）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPlanProposalVO propose(Long routeId) {
        DeliveryRouteEntity route = deliveryQueryDao.lockRoute(routeId);
        if (route == null) {
            throw new ScmBusinessException(DeliveryErrorCode.NOT_FOUND);
        }
        requireDraft(route);
        List<DeliveryRouteStopEntity> stops = stops(routeId);
        requireLocations(route, stops);

        List<GeoPoint> points = new ArrayList<>(stops.size() + 1);
        points.add(new GeoPoint(route.getStartLongitude(), route.getStartLatitude(), route.getStartGeomCrs()));
        for (DeliveryRouteStopEntity stop : stops) {
            points.add(new GeoPoint(stop.getLongitude(), stop.getLatitude(), stop.getGeomCrs()));
        }

        BigDecimal[][] matrix = distanceMatrixProvider.matrix(points);
        NearestNeighbourPlanner.Plan plan = NearestNeighbourPlanner.plan(matrix);

        List<Map<String, Object>> legs = new ArrayList<>();
        List<DeliveryPlanLegVO> legVos = new ArrayList<>();
        for (int position = 1; position < plan.order().size(); position++) {
            // order 的元素是 points 的下标：0 是起点，k 对应 stops.get(k - 1)
            DeliveryRouteStopEntity stop = stops.get(plan.order().get(position) - 1);
            Map<String, Object> leg = new LinkedHashMap<>();
            leg.put("seq", position);
            leg.put("stopId", stop.getId());
            leg.put("legDistance", plain(plan.legDistances().get(position)));
            leg.put("cumulativeDistance", plain(plan.cumulativeDistances().get(position)));
            legs.add(leg);

            DeliveryPlanLegVO legVo = new DeliveryPlanLegVO();
            legVo.setSeq(position);
            legVo.setStopId(stop.getId());
            legVo.setCustomerNameSnapshot(stop.getCustomerNameSnapshot());
            legVo.setAddressSnapshot(stop.getAddressSnapshot());
            legVo.setLegDistance(plan.legDistances().get(position));
            legVo.setCumulativeDistance(plan.cumulativeDistances().get(position));
            legVo.setLongitude(stop.getLongitude());
            legVo.setLatitude(stop.getLatitude());
            legVos.add(legVo);
        }

        String operator = ScmOperator.current();
        // 旧的待确认建议先作废：并存两个待确认建议会让「应用哪一个」变成猜
        deliveryPlanProposalDao.discardActive(routeId, operator);

        DeliveryPlanProposalEntity row = new DeliveryPlanProposalEntity();
        row.setRouteId(routeId);
        row.setStatus("PROPOSED");
        row.setProviderCode(distanceMatrixProvider.providerCode());
        row.setProviderVersion(distanceMatrixProvider.providerVersion());
        row.setEstimatedFlag(distanceMatrixProvider.estimated());
        row.setRuleCode(NearestNeighbourPlanner.RULE_CODE);
        row.setStopCount(stops.size());
        row.setTotalDistance(plan.totalDistance());
        row.setInputSnapshot(inputSnapshot(route, stops));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleCode", NearestNeighbourPlanner.RULE_CODE);
        result.put("legs", legs);
        result.put("totalDistance", plain(plan.totalDistance()));
        row.setResultSnapshot(result);
        row.setCreatedAt(OffsetDateTime.now());
        row.setCreatedBy(operator);
        deliveryPlanProposalDao.insertProposal(row);

        DeliveryPlanProposalVO vo = toVO(row);
        vo.setLegs(legVos);
        return vo;
    }

    /**
     * 应用建议：按建议顺序重排停靠点，并把建议标记为已应用。
     *
     * <p>
     * 顺序写入沿用与手工排序相同的做法（先把序号整体挪到不相交的正数区间，再逐个赋值），
     * 因为 {@code (route_id, stop_seq)} 上有活动行唯一索引，直接互换会中途撞唯一键。
     */
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long proposalId, DeliveryPlanApplyForm form) {
        DeliveryPlanProposalEntity proposal = deliveryPlanProposalDao.lockById(proposalId);
        if (proposal == null) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_NOT_FOUND);
        }
        if (!"PROPOSED".equals(proposal.getStatus())) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }
        DeliveryRouteEntity route = deliveryQueryDao.lockRoute(proposal.getRouteId());
        if (route == null) {
            throw new ScmBusinessException(DeliveryErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(route.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
        // 已规划 / 已发车的线路不再接受顺序改写：建议可能是在线路还是草稿时生成的
        requireDraft(route);

        List<Long> orderedStopIds = orderedStopIds(proposal);
        List<DeliveryRouteStopEntity> existing = stops(route.getId());
        Set<Long> existingIds = existing.stream().map(DeliveryRouteStopEntity::getId).collect(Collectors.toSet());
        if (orderedStopIds.size() != existing.size() || !new HashSet<>(orderedStopIds).equals(existingIds)) {
            // 建议生成后停靠点被增删过：直接按旧顺序写回会漏掉新点或指向已删点
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }

        String operator = ScmOperator.current();
        deliveryQueryDao.bumpStopSequences(route.getId());
        Map<Long, DeliveryRouteStopEntity> byId = new HashMap<>();
        existing.forEach(stop -> byId.put(stop.getId(), stop));
        int seq = 0;
        for (Long stopId : orderedStopIds) {
            DeliveryRouteStopEntity stop = byId.get(stopId);
            stop.setStopSeq(++seq);
            stamp(stop);
            deliveryRouteStopDao.updateById(stop);
        }
        // 线路版本必须推进：否则客户端拿着旧版本号还能再排一次，两次顺序会互相覆盖
        stamp(route);
        if (deliveryRouteDao.updateById(route) != 1) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
        if (deliveryPlanProposalDao.markApplied(proposal.getId(), proposal.getVersion(), operator) != 1) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }
    }

    /**
     * 放弃建议：不改线路。
     */
    @Transactional(rollbackFor = Exception.class)
    public void discard(Long proposalId, DeliveryPlanDiscardForm form) {
        DeliveryPlanProposalEntity proposal = deliveryPlanProposalDao.lockById(proposalId);
        if (proposal == null) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_NOT_FOUND);
        }
        if (!Objects.equals(proposal.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
        if (deliveryPlanProposalDao.markDiscarded(proposalId, form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }
    }

    /**
     * 某线路的建议历史（最新在前），供比较多次生成的结果。
     */
    @Transactional(readOnly = true)
    public List<DeliveryPlanProposalVO> history(Long routeId) {
        return deliveryPlanProposalDao.listByRoute(routeId, HISTORY_LIMIT).stream()
                .map(DeliveryPlanProposalService::toVO).toList();
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private List<DeliveryRouteStopEntity> stops(Long routeId) {
        return deliveryRouteStopDao.selectList(new LambdaQueryWrapper<DeliveryRouteStopEntity>()
                .eq(DeliveryRouteStopEntity::getRouteId, routeId)
                .orderByAsc(DeliveryRouteStopEntity::getStopSeq));
    }

    private static void requireDraft(DeliveryRouteEntity route) {
        if (!ScmDeliveryRouteStatusEnum.DRAFT.name().equals(route.getStatus())) {
            throw new ScmBusinessException(DeliveryErrorCode.STATE_INVALID);
        }
    }

    /**
     * 起点与全部停靠点都要有坐标且同一坐标系 —— 与 {@code DeliveryRouteService#plan} 同一条规则，
     * 因为排线建议喂给它的正是这份坐标。
     */
    private static void requireLocations(DeliveryRouteEntity route, List<DeliveryRouteStopEntity> stops) {
        if (route.getStartLongitude() == null || route.getStartLatitude() == null
                || route.getStartGeomCrs() == null || stops.isEmpty()) {
            throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
        }
        for (DeliveryRouteStopEntity stop : stops) {
            if (stop.getLongitude() == null || stop.getLatitude() == null
                    || !route.getStartGeomCrs().equals(stop.getGeomCrs())) {
                throw new ScmBusinessException(DeliveryErrorCode.LOCATION_REQUIRED);
            }
        }
    }

    /**
     * 输入快照：冻结「按什么算的」。
     *
     * <p>
     * {@code constraints} 同时列出**生效**与**未生效**的约束：只写生效项会让人以为
     * 建议已经把时间窗、载重都考虑进去了，而当前规则一个都没考虑。
     */
    private static Map<String, Object> inputSnapshot(DeliveryRouteEntity route, List<DeliveryRouteStopEntity> stops) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("longitude", plain(route.getStartLongitude()));
        start.put("latitude", plain(route.getStartLatitude()));
        start.put("geomCrs", route.getStartGeomCrs());
        snapshot.put("start", start);

        List<Map<String, Object>> stopRows = new ArrayList<>();
        for (DeliveryRouteStopEntity stop : stops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stopId", stop.getId());
            item.put("stopSeq", stop.getStopSeq());
            item.put("customerNameSnapshot", stop.getCustomerNameSnapshot());
            item.put("addressSnapshot", stop.getAddressSnapshot());
            item.put("longitude", plain(stop.getLongitude()));
            item.put("latitude", plain(stop.getLatitude()));
            stopRows.add(item);
        }
        snapshot.put("stops", stopRows);

        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("applied", List.of("SAME_CRS", "START_FIXED"));
        constraints.put("notApplied", List.of("TIME_WINDOW", "VEHICLE_CAPACITY", "TRAFFIC"));
        snapshot.put("constraints", constraints);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> orderedStopIds(DeliveryPlanProposalEntity proposal) {
        List<Long> ids = new ArrayList<>();
        Object legs = proposal.getResultSnapshot() == null ? null : proposal.getResultSnapshot().get("legs");
        if (!(legs instanceof List<?> list)) {
            throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> leg) || leg.get("stopId") == null) {
                throw new ScmBusinessException(DeliveryErrorCode.PLAN_PROPOSAL_STATE_INVALID);
            }
            ids.add(Long.valueOf(String.valueOf(leg.get("stopId"))));
        }
        return ids;
    }

    private static DeliveryPlanProposalVO toVO(DeliveryPlanProposalEntity row) {
        DeliveryPlanProposalVO vo = new DeliveryPlanProposalVO();
        vo.setId(row.getId());
        vo.setRouteId(row.getRouteId());
        vo.setStatus(row.getStatus());
        vo.setProviderCode(row.getProviderCode());
        vo.setProviderVersion(row.getProviderVersion());
        vo.setEstimated(Boolean.TRUE.equals(row.getEstimatedFlag()));
        vo.setRuleCode(row.getRuleCode());
        vo.setStopCount(row.getStopCount());
        vo.setTotalDistance(row.getTotalDistance());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        vo.setAppliedAt(row.getAppliedAt());
        vo.setAppliedBy(row.getAppliedBy());
        vo.setDiscardedAt(row.getDiscardedAt());
        vo.setDiscardedBy(row.getDiscardedBy());
        vo.setVersion(row.getVersion());
        return vo;
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void stamp(DeliveryRecord entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
    }
}
