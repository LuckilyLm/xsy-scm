package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.order.domain.entity.IdempotencyRecordEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface IdempotencyRecordDao extends BaseMapper<IdempotencyRecordEntity> {
    IdempotencyRecordEntity lock(@Param("id") Long id);

    int claim(IdempotencyRecordEntity row);

    IdempotencyRecordEntity find(@Param("scope") String scope, @Param("key") String key);
}
