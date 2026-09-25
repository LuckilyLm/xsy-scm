package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 财务操作日志读写。
 *
 * <p><b>只有 insert 与 select 两种用法</b>：本表连 {@code deleted} 与 {@code version} 列都没有，
 * append-only 是结构性的 —— 没有可以翻转的软删标记，也没有可被部分更新的列。
 * 唯一写入口是 {@code FinanceOperationLogRecorder}，不得在别处直接 insert，
 * 否则操作人取值与 JSON 列形态会各写一份、迟早分叉（与 {@code OrderOperationLogDao} 同一理由）。
 */
@Mapper
public interface FinanceOperationLogDao extends BaseMapper<FinanceOperationLogEntity> {
}
