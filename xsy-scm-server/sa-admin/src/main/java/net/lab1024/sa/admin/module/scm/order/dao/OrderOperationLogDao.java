package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.order.domain.entity.OrderOperationLogEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface OrderOperationLogDao {
    int insert(OrderOperationLogEntity row);

    /** 列表读；日志行本身无归属列，范围经父订单收窄，见 OrderOperationLogMapper.xml。 */
    List<OrderOperationLogEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
                                        @Param("scope") ScmValueScope scope);
}
