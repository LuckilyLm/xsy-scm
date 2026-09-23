package net.lab1024.sa.admin.module.scm.delivery.dao;

import org.apache.ibatis.annotations.*;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;

@Mapper
public interface DeliveryQueryDao {
    List<DeliveryRouteVO> routes(Page<?> page, @Param("q") DeliveryQueryForm q);

    DeliveryRouteVO route(@Param("id") Long id);

    DeliveryRouteEntity lockRoute(@Param("id") Long id);

    List<DeliveryStopVO> stops(@Param("id") Long id);

    List<DeliveryCandidateVO> candidates(Page<?> page, @Param("q") DeliveryQueryForm q, @Param("statuses") List<String> statuses);

    DeliveryCandidateVO candidate(@Param("id") Long id);

    Long nextNumber();

    int bumpStopSequences(@Param("id") Long id);

    List<SalesOrderItemEntity> printItems(@Param("id") Long id);

    List<DeliveryOrderViewVO> orderView(@Param("id") Long id);

    List<DeliveryCustomerViewVO> customerView(@Param("id") Long id);

    int markPrinted(@Param("ids") List<Long> assignmentIds, @Param("operator") String operator);
}
