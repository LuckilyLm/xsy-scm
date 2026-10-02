package com.xsy.scm.delivery.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.DeliveryErrorCode;
import com.xsy.scm.delivery.dao.DeliveryGpsEventDao;
import com.xsy.scm.delivery.domain.entity.DeliveryGpsEventEntity;
import com.xsy.scm.delivery.domain.form.DeliveryGpsQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryGpsReportForm;
import com.xsy.scm.delivery.domain.vo.DeliveryGpsEventVO;
import com.xsy.scm.delivery.domain.vo.DeliveryRouteVO;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GPS 轨迹：上报与查询 / 回放。
 *
 * <p>
 * 三条不变量：
 * <ul>
 * <li><b>范围与线路读侧同源</b>：上报与查询都先经 {@link DeliveryRouteQueryService#requireVisible(Long)}，
 * 因此司机只能碰自己的线路，且「线路看不到、轨迹看得到」这条旁路不存在。</li>
 * <li><b>重复上报不是错误</b>：{@code event_key} 冲突时回既有记录并标记 {@code duplicated}。
 * 弱网重试是常态，把重试报错会让客户端一直重试；但也不能静默 —— 那会让「我上报了 10 个点、
 * 库里只有 3 个」无从解释。</li>
 * <li><b>轨迹只是证据</b>：本类不写订单、不写库存、不写财务，也不改签收结果。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeliveryGpsService {

    private static final int MAX_QUERY_POINTS = 2000;

    private static final int DEFAULT_QUERY_POINTS = 500;

    private final DeliveryGpsEventDao deliveryGpsEventDao;

    private final DeliveryRouteQueryService deliveryRouteQueryService;

    /**
     * 上报一个轨迹点。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryGpsEventVO report(DeliveryGpsReportForm form) {
        DeliveryRouteVO route = deliveryRouteQueryService.requireVisible(form.getRouteId());
        // 设备时钟不可信，但不接受明显超前的采集时间：那会让回放顺序错乱，且库里 CHECK 也会拒绝
        if (form.getCapturedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new ScmBusinessException(DeliveryErrorCode.GPS_CAPTURED_AT_INVALID);
        }

        DeliveryGpsEventEntity row = new DeliveryGpsEventEntity();
        row.setEventKey(form.getEventKey().trim());
        row.setRouteId(form.getRouteId());
        // 司机取自线路的指派司机，不取客户端传值：客户端传的司机 id 不可信，
        // 而轨迹归属本来就应该跟随线路（改派后新上报的点自然归新司机）
        row.setDriverId(route.getDriverId());
        row.setDeviceCode(blankToNull(form.getDeviceCode()));
        row.setCapturedAt(form.getCapturedAt());
        row.setReceivedAt(OffsetDateTime.now());
        row.setLongitude(form.getLongitude());
        row.setLatitude(form.getLatitude());
        row.setGeomCrs(form.getGeomCrs());
        row.setAccuracyMeters(form.getAccuracyMeters());
        row.setSpeedKph(form.getSpeedKph());
        row.setReportedBy(ScmOperator.current());

        if (deliveryGpsEventDao.insertIgnore(row) == 1) {
            return toVO(row, false);
        }
        DeliveryGpsEventEntity existing = deliveryGpsEventDao.selectByEventKey(row.getEventKey());
        if (existing == null) {
            // 插入报告冲突却读不到行：唯一索引与查询条件不一致，属数据/映射缺陷
            throw new IllegalStateException("GPS 事件插入冲突但重读为空，eventKey=" + row.getEventKey());
        }
        return toVO(existing, true);
    }

    /**
     * 查询 / 回放：按**采集时间**升序返回。
     */
    @Transactional(readOnly = true)
    public List<DeliveryGpsEventVO> query(DeliveryGpsQueryForm form) {
        deliveryRouteQueryService.requireVisible(form.getRouteId());
        int limit = form.getLimit() == null ? DEFAULT_QUERY_POINTS : Math.min(form.getLimit(), MAX_QUERY_POINTS);
        return deliveryGpsEventDao.listByRoute(form.getRouteId(), form.getFrom(), form.getTo(), limit).stream()
                .map(row -> toVO(row, false)).toList();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static DeliveryGpsEventVO toVO(DeliveryGpsEventEntity row, boolean duplicated) {
        DeliveryGpsEventVO vo = new DeliveryGpsEventVO();
        vo.setId(row.getId());
        vo.setEventKey(row.getEventKey());
        vo.setRouteId(row.getRouteId());
        vo.setDriverId(row.getDriverId());
        vo.setDeviceCode(row.getDeviceCode());
        vo.setCapturedAt(row.getCapturedAt());
        vo.setReceivedAt(row.getReceivedAt());
        vo.setLongitude(row.getLongitude());
        vo.setLatitude(row.getLatitude());
        vo.setGeomCrs(row.getGeomCrs());
        vo.setAccuracyMeters(row.getAccuracyMeters());
        vo.setSpeedKph(row.getSpeedKph());
        vo.setReportedBy(row.getReportedBy());
        vo.setDuplicated(duplicated);
        return vo;
    }
}
