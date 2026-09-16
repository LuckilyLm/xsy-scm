package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;
import java.util.List;
import net.lab1024.sa.admin.module.scm.order.domain.entity.OrderAddressSnapshotEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;
@Mapper
public interface OrderAddressSnapshotDao extends BaseMapper<OrderAddressSnapshotEntity> {
    List<OrderAddressSnapshotEntity> list(@Param("id") Long id);
}
