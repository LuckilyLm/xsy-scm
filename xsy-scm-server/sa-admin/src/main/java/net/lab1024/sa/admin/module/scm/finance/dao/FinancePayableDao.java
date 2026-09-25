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
}
