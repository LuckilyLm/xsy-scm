package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 财务操作日志只新增和查询，不允许修改或删除；写入必须通过统一日志服务，保持操作人和数据格式一致。 */
@Mapper
public interface FinanceOperationLogDao extends BaseMapper<FinanceOperationLogEntity> {
}
