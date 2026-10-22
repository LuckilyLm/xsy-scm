package com.xsy.scm.trace.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 包装码与包装事件的读写。包装事件只追加；包装码可作废但不可删除、码不复用。SQL 见 mapper/scm/trace/TraceUnitDao.xml。 */
@Mapper
public interface TraceUnitDao {

    int createUnit(TraceUnitVO.Unit unit);

    int appendIssueEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator,
            @Param("remark") String remark);

    TraceUnitVO.Lot findLot(@Param("lotId") Long lotId);

    /** 该批次已发码的包装数量之和（含已作废的码：作废不等于没发过）。 */
    BigDecimal sumIssuedByLot(@Param("lotId") Long lotId);

    /** 批次余额行上的记账单位；发码沿用同一个单位，不做换算。 */
    String lotUnit(@Param("lotId") Long lotId);

    /** 该批次当前余额（跨仓库求和）：发码总量不得超过它。 */
    BigDecimal lotBalance(@Param("lotId") Long lotId);

    /** 锁定该批次在全部仓库的批次余额行：发码前必须持锁，否则两个事务会各自读到同一余额。 */
    List<LotBalanceRow> lockLotBalances(@Param("lotId") Long lotId);

    /** 批次余额行（加锁读）。 */
    @Data
    class LotBalanceRow {
        private Long id;
        private BigDecimal quantity;
    }

    /** 批次归属仓库：由首次收货的收货单决定，包装码与批次共用同一个仓库。 */
    Long lotWarehouseId(@Param("lotId") Long lotId);

    List<TraceUnitVO.Unit> queryPage(Page<?> page, @Param("query") TraceUnitQueryForm query,
            @Param("scope") ScmValueScope scope);

    TraceUnitVO.Unit findByCode(@Param("unitCode") String unitCode);

    /** 按码加锁读：绑定与作废都要先拿这把锁，否则会留下「已作废的码挂着绑定」。 */
    TraceUnitVO.Unit lockByCode(@Param("unitCode") String unitCode);

    TraceUnitVO.Unit findById(@Param("id") Long id);

    /** 审批更正时锁住与扫码绑定/作废共用的包装单元行。 */
    TraceUnitVO.Unit lockById(@Param("id") Long id);

    /** 批次首次入库的收货来源。 */
    TraceUnitVO.Receipt receiptOfLot(@Param("lotId") Long lotId);

    /** 分拣任务行：绑定校验的客户/商品/实拣量来源；锁行以串行化同一行上的并发绑定。 */
    TraceUnitVO.SortingItem lockSortingItem(@Param("itemId") Long itemId);

    /** 该分拣行已绑定的包装数量之和（只算有效绑定）：绑定总量不得超过实拣量。 */
    BigDecimal sumBoundBySortingItem(@Param("itemId") Long itemId);

    /** 分拣行对应订单行如已确认出库，禁止事后补绑新的包装码。 */
    int countConfirmedOutboundBySortingItem(@Param("itemId") Long itemId);

    int insertBinding(@Param("unitId") Long unitId, @Param("lotId") Long lotId, @Param("taskId") Long taskId,
            @Param("itemId") Long itemId, @Param("salesOrderId") Long salesOrderId,
            @Param("salesOrderItemId") Long salesOrderItemId, @Param("customerId") Long customerId,
            @Param("customerName") String customerName, @Param("orderNo") String orderNo,
            @Param("boundAt") OffsetDateTime boundAt, @Param("operator") String operator);

    int appendBindEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId, @Param("itemId") Long itemId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator);

    int markVoid(@Param("id") Long id);

    int appendVoidEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator,
            @Param("reason") String reason);

    /** 补录净重：只对还没有重量的码生效，返回受影响行数由调用方判定是否已被并发补录。 */
    int updateWeight(@Param("id") Long id, @Param("netWeight") BigDecimal netWeight,
            @Param("weightUnit") String weightUnit, @Param("weightSource") String weightSource,
            @Param("measuredAt") OffsetDateTime measuredAt);

    /** 追加称重事件：补录与设备称重走同一条事件类型，事后无法区分是谁在什么时候改了重量。 */
    int appendWeighEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator,
            @Param("remark") String remark);

    /** 按标签上的公开令牌查码：扫码页免登录，只认这个凭据。 */
    TraceUnitVO.Unit findByPublicToken(@Param("publicToken") String publicToken);

    /** 不加锁读分拣行：秤端选批次时只需要商品与仓库，锁行会与并发的分拣写入互相等待。 */
    TraceUnitVO.SortingItem findSortingItem(@Param("itemId") Long itemId);

    /** 本仓该商品还有余额的批次，按先进先出排序：现场默认取第一个，避免每次都要人工判断。 */
    List<TraceUnitVO.LotOption> candidateLots(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    /** 称重事件归属与完成状态：发码前要确认这筐属于该分拣行且还没被完成覆盖。 */
    WeighingEvent findWeighingEvent(@Param("eventId") Long eventId);

    /** 称重事件行（只取发码需要的几列）。 */
    @Data
    class WeighingEvent {
        private Long id;
        private Long taskItemId;
        private BigDecimal netWeight;
        private String businessUnit;
        private Long completionId;
    }

    /** 已经发过码的称重事件对应的包装码：重试发码时原样返回，不再发第二枚。 */
    TraceUnitVO.Unit findByWeighingEventId(@Param("weighingEventId") Long weighingEventId);

    /** 该分拣行上「有净重但还没绑定」的包装码：明细完成时整批建立绑定。 */
    List<TraceUnitVO.Unit> listUnboundWeighedUnits(@Param("itemId") Long itemId);

    /** 该包装码当前有效绑定的 id（没有返回 null）：更正申请要指向具体那一条绑定。 */
    Long findActiveBindingId(@Param("unitId") Long unitId);

    /** 该包装码的有效绑定事实（未绑定或已解绑返回 null）。 */
    TraceUnitVO.Binding findBinding(@Param("unitId") Long unitId);

    /** 该批次参与过的销售出库（按批次流水汇总到出库单）。 */
    List<TraceUnitVO.Outbound> outboundsOfLot(@Param("lotId") Long lotId);
}
