package net.lab1024.sa.admin.module.scm.delivery.dao;

import org.apache.ibatis.annotations.*;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;

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

    Long nextNumber();

    int bumpStopSequences(@Param("id") Long id);

    List<SalesOrderItemEntity> printItems(@Param("id") Long id);

    List<DeliveryOrderViewVO> orderView(@Param("id") Long id);

    List<DeliveryCustomerViewVO> customerView(@Param("id") Long id);

    int markPrinted(@Param("ids") List<Long> assignmentIds, @Param("operator") String operator);
}
