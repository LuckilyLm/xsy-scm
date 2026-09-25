package net.lab1024.sa.admin.module.scm.delivery.dao;

import org.apache.ibatis.annotations.*;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;

/**
 * 配送读侧取数。带 {@code scope} 参数的语句是**只读列表 / 详情**，必须由 Service 显式下传数据范围，
 * 传 {@code null} 在 Mapper 里渲染成恒假谓词（失败关闭）；写侧用的
 * {@link #lockRoute}、{@link #stops}、{@link #candidate}、{@link #markPrinted} 故意不带范围参数，
 * 否则司机侧的读取收窄会顺手把主管的调度能力一起关掉。
 */
@Mapper
public interface DeliveryQueryDao {
    List<DeliveryRouteVO> routes(Page<?> page, @Param("q") DeliveryQueryForm q, @Param("scope") ScmValueScope scope);

    DeliveryRouteVO route(@Param("id") Long id, @Param("scope") ScmValueScope scope);

    DeliveryRouteEntity lockRoute(@Param("id") Long id);

    List<DeliveryStopVO> stops(@Param("id") Long id);

    List<DeliveryCandidateVO> candidates(Page<?> page, @Param("q") DeliveryQueryForm q, @Param("statuses") List<String> statuses,
                                         @Param("scope") ScmValueScope scope);

    DeliveryCandidateVO candidate(@Param("id") Long id);

    List<DeliveryCandidateVO> candidateByIds(@Param("ids") List<Long> ids);

    /**
     * 尚未被「已完成」分拣任务覆盖的有效明细行数；0 才允许进入配送候选。
     * 与 {@code candidateSource} 里的覆盖谓词同一条口径，两处必须一起改。
     */
    int unsortedItemCount(@Param("orderId") Long orderId);

    /**
     * 发车取实发量：给定订单的每条有效明细行，取其被 COMPLETED 任务的 ACTIVE 明细覆盖时的实发量。
     *
     * <p>与 {@link #unsortedItemCount} 是**同一条件的正反两面**，两处必须一起改：资格判定说
     * 「每行都被覆盖」，这里就保证「每行都能取到一行量」。只改一边会出现
     * 「订单合格、发车却静默少发一行」。未被覆盖的订单行不出现在结果里，由服务侧逐单核对行数。
     */
    List<net.lab1024.sa.admin.module.scm.delivery.domain.dto.DeliverySortedLine> sortedLines(
            @Param("orderIds") java.util.Collection<Long> orderIds);

    Long nextNumber();

    int bumpStopSequences(@Param("id") Long id);

    List<DeliveryPrintItemVO> printItems(@Param("id") Long id);

    List<DeliveryOrderViewVO> orderView(@Param("id") Long id);

    List<DeliveryCustomerViewVO> customerView(@Param("id") Long id);

    int markPrinted(@Param("ids") List<Long> assignmentIds, @Param("operator") String operator);

    /**
     * 发车时把该线路全部活动订单推到 {@code IN_TRANSIT}。
     *
     * <p>返回行数由调用方与活动订单数比对 —— 数量不等说明锁内读到的集合与这条 UPDATE 命中的
     * 集合不一致（有并发在中间改过占用位），整条发车必须回滚而不是留下一半状态。
     */
    int markInTransit(@Param("routeId") Long routeId, @Param("operator") String operator);

    /**
     * 签收：状态条件与 {@code version} 一起进 WHERE，因此「重复签收」与「已不在线路上」都表现为 0 行。
     */
    int markSigned(@Param("id") Long assignmentId, @Param("version") Integer version,
                   @Param("result") String result, @Param("reason") String reason,
                   @Param("operator") String operator);

    /**
     * 该线路尚未进入终态的活动订单数，完成线路的前置判定。
     */
    int countUnfinished(@Param("routeId") Long routeId);
}
