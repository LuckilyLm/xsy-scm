package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceiptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收款读写。
 *
 * <p><b>登错只能反向，不能改</b>（D-3）：本接口刻意不声明 update / delete，
 * {@code ck_finance_receipt_append_only} 在库层拒绝软删，
 * {@code uk_finance_receipt_single_reverse} 保证一条 {@code NORMAL} 最多被反向一次。
 *
 * <p>F1-3 的登记与 F1-4 的反向都必须先 {@code SELECT … FOR UPDATE} 锁住原行
 * （{@code FinanceConstant.LOCK_RANK_RECEIPT}），再校验已用额；反向与核销共用这把锁，
 * 因此「反向前已用额 = 0」这条前置在并发下才成立。
 */
@Mapper
public interface FinanceReceiptDao extends BaseMapper<FinanceReceiptEntity> {

    /**
     * 收款单号序列（全局非重置，不按日归零）。必须在事务内调用：
     * {@code nextval} 不随事务回滚，跳号是可接受的代价（与应付单号同一条纪律）。
     */
    long nextReceiptNo();
}
