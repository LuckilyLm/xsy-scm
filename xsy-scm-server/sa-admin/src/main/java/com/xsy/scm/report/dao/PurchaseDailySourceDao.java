package com.xsy.scm.report.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.report.domain.dto.PurchaseDailySnapshotRow;

/** 后台全量生成专用的跨域只读契约，不向交互查询开放。 */
@Mapper
public interface PurchaseDailySourceDao {
    List<PurchaseDailySnapshotRow> aggregateSubmittedOrders(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("statuses") List<ScmPurchaseStatusEnum> statuses);
}
