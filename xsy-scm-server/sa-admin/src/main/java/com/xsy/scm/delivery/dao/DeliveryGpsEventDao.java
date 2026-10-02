package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.delivery.domain.entity.DeliveryGpsEventEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * GPS 轨迹事件读写。
 *
 * <p>
 * 只追加、不更新、不删除：轨迹是配送证据，能改就不是证据。
 */
@Mapper
public interface DeliveryGpsEventDao extends BaseMapper<DeliveryGpsEventEntity> {

    /**
     * 追加一条事件；{@code event_key} 冲突返回 0（重复上报），调用方重读既有记录。
     */
    int insertIgnore(@Param("row") DeliveryGpsEventEntity row);

    /**
     * 按事件键取既有记录（重复上报时回给客户端同一份结果）。
     */
    DeliveryGpsEventEntity selectByEventKey(@Param("eventKey") String eventKey);

    /**
     * 某线路的轨迹点，按**采集时间**升序（回放顺序）；同一采集时间按 id 稳定排序。
     */
    List<DeliveryGpsEventEntity> listByRoute(@Param("routeId") Long routeId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("limit") int limit);
}
