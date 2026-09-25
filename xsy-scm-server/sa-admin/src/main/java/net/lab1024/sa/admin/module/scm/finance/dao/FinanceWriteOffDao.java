package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceWriteOffEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 核销行读写。
 *
 * <p><b>撤销 = 新增 {@code REVERSE} 行</b>（Q18）：本接口刻意不声明 update / delete。
 * {@code ck_finance_write_off_append_only} 拒绝软删，
 * {@code uk_finance_write_off_single_reverse} 保证一条 {@code NORMAL} 最多被反向一次 ——
 * 重复撤销在库级失败，而不是靠服务层先查后判。
 *
 * <p>已核销额 / 未核销额 / 结清状态**一律读时派生**（Q17）：F1-5 在此追加聚合查询，
 * 但不得追加任何「把派生值写回本表或应收应付表」的方法（全局不变量 6）。
 */
@Mapper
public interface FinanceWriteOffDao extends BaseMapper<FinanceWriteOffEntity> {
}
