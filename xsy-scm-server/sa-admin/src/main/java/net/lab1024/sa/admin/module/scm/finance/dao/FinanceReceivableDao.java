package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceivableEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应收单头读写。
 *
 * <p><b>append-only 的接口形态</b>：本接口刻意不声明任何 update / delete 方法 ——
 * 应收一经生成即不可改，红冲是新增一条 {@code entry_type = 'RED'} 的行。
 * 表上的 {@code ck_finance_receivable_append_only CHECK (deleted = FALSE)} 在数据库层兜底，
 * 因此即使有人绕过 DAO 直接写 SQL 去软删历史应收，也会被 PostgreSQL 拒绝。
 *
 * <p>{@code BaseMapper} 继承来的 {@code updateById} / {@code deleteById} 在财务域**不得使用**；
 * 实体不带 {@code @TableLogic}，所以 {@code deleteById} 会生成一条真实的 {@code DELETE}，
 * 而 {@code deleted = TRUE} 的更新会撞 CHECK。两者都不是可用的纠错路径（全局不变量 1/2）。
 *
 * <p>F1-2 在此追加 {@code insertOnConflictDoNothing}：冲突目标必须与
 * {@code uk_finance_receivable_source_active} 的谓词逐字一致（Q11 同一条纪律），
 * 命中冲突即「已生成」并返回成功 —— 财务生成是可重放的派生，不是用户命令。
 */
@Mapper
public interface FinanceReceivableDao extends BaseMapper<FinanceReceivableEntity> {

    /**
     * 应收单号序列（全局非重置，不按日归零）。
     *
     * <p>必须在事务内调用：{@code nextval} 不随事务回滚，跳号是可接受的代价。
     */
    long nextReceivableNo();

    /**
     * 插入正常应收，来源订单已生成过应收时什么都不做。
     *
     * <p>冲突目标与 {@code uk_finance_receivable_source_active} 的列和谓词逐字一致
     * （Q11 纪律；本索引刻意<b>不含</b> {@code source_id IS NOT NULL} —— 应收来源恒非空）。
     *
     * @return 1 = 本次生成了应收（{@code id} 已回填）；0 = 该订单已有应收，调用方按「已生成」成功返回
     */
    int insertOnConflictDoNothing(FinanceReceivableEntity entity);
}
