package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePayableEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应付单头读写，append-only 契约同 {@link FinanceReceivableDao}。
 *
 * <p>正常应付的行级防重锚点是 {@code uk_finance_payable_source_active}
 * （谓词含 {@code source_id IS NOT NULL}）；手工红字应付的 {@code source_id} 为 NULL，
 * 落在该谓词之外，防重由「可冲上限 41137 + 请求级幂等键」承担。
 */
@Mapper
public interface FinancePayableDao extends BaseMapper<FinancePayableEntity> {

    /**
     * 应付单号序列（全局非重置，不按日归零）。
     *
     * <p>必须在事务内调用：{@code nextval} 不随事务回滚，跳号是可接受的代价。
     */
    long nextPayableNo();

    /**
     * 插入正常应付，来源身份已存在时什么都不做。
     *
     * <p>冲突目标与 {@code uk_finance_payable_source_active} 的列和谓词**逐字一致**（Q11 纪律），
     * 不使用无目标的 {@code ON CONFLICT DO NOTHING} —— 无目标写法会把 {@code payable_no} 撞号
     * 也一起吞掉，那是一笔永远不会被发现的单号异常。
     *
     * @return 1 = 本次生成了应付（{@code id} 已回填）；0 = 该收货单已有应付，调用方按「已生成」成功返回
     */
    int insertOnConflictDoNothing(FinancePayableEntity entity);
}
