package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.form.OrderPoolQueryForm;

@Mapper
public interface SalesOrderDao extends BaseMapper<SalesOrderEntity> {
    List<SalesOrderEntity> unassignedMallOrders(Page<?> page, @Param("query") OrderPoolQueryForm query);

    int assignUnassignedMallOrder(@Param("orderId") Long orderId, @Param("version") Integer version,
        @Param("sellerId") Long sellerId, @Param("operator") String operator);

    Long customerReferences(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderEntity lock(@Param("id") Long id);

    boolean hasPriceFreezeBlockingFact(@Param("orderId") Long orderId);

    /** 是否已有分拣任务或出库记录：有这些事实后增删商品会让分拣与出库对不上单。 */
    boolean hasSortingOrOutbound(@Param("orderId") Long orderId);

    /**
     * 列表查询；{@code scope} 必须由 Service 通过 {@code ScmDataScopeService#resolve()} 传入，传 {@code null} 时 SQL
     * 渲染成恒假条件，按拒绝处理。{@code lock} / {@code selectById} 等写路径不带范围。
     */
    List<SalesOrderEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
        @Param("scope") ScmValueScope scope);

    List<SalesOrderEntity> listByCustomer(Page<?> page, @Param("customerId") Long customerId,
        @Param("displayStatus") String displayStatus);

    /**
     * 客户分拣单/送货单的批量取单：按期望配送日期、线路和客户筛选，只取已确认订单。 {@code scope} 必须由 Service 传入，null 按拒绝处理；返回顺序即打印顺序。
     */
    List<SalesOrderEntity> selectForCustomerSheet(@Param("start") java.time.OffsetDateTime start,
        @Param("end") java.time.OffsetDateTime end, @Param("scope") ScmValueScope scope, @Param("routeId") Long routeId,
        @Param("customerIds") List<Long> customerIds);

    SalesOrderEntity selectByIdAndCustomer(@Param("orderId") Long orderId, @Param("customerId") Long customerId);

    /** 发车出库时记录时间，7 天自动锁单从这一刻起算。 */
    int markDispatched(@Param("orderId") Long orderId, @Param("at") java.time.OffsetDateTime at,
        @Param("operator") String operator);

    /** 待自动锁单的订单：已发车、未锁、发车满 7 天。 */
    List<Long> autoLockCandidates(@Param("limit") int limit);

    /** 条件锁单：未锁且仍存在时写入，返回 1 表示本次真的锁上。 */
    int lockIfUnlocked(@Param("orderId") Long orderId, @Param("at") java.time.OffsetDateTime at,
        @Param("operator") String operator, @Param("reason") String reason,
        @Param("autoLockAt") java.time.OffsetDateTime autoLockAt);

    /** 解锁：只有已锁的订单会被解锁，返回 1 表示本次真的解锁。 */
    int unlock(@Param("orderId") Long orderId, @Param("operator") String operator);

    Long nextOrder();

    Long nextReturn();

    Long nextRefund();
}
