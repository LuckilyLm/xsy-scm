package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceWriteOffDao;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 核销域服务（F1-1 骨架，尚无业务方法）。
 *
 * <p><b>F1-4 在此实现</b>：M:N 核销（{@code scm:finance:write-off:add}）与反向核销
 * （{@code scm:finance:write-off:reverse}，Q18）。
 *
 * <p>核销校验全部在**持有目标锁之后**做（设计稿 §7 / §14）：
 * ① source 与 target 的结算对方必须一致，否则 {@code 41136}；
 * ② 本次金额 ≤ 目标 {@code openAmount}，否则 {@code 41135}；
 * ③ 本次金额 ≤ source 待核销余额，否则同上。
 * {@code openAmount = max(netAmount − writtenOffAmount, 0)}，因此 D-4 下净应收为负的单据
 * {@code openAmount = 0}、自然不可再被核销，不需要为负数另设分支。
 *
 * <p><b>F1-1 的硬边界</b>：不得把已核销额 / 结清状态写回任何表（Q17，全局不变量 6）；
 * 撤销只能新增 {@code REVERSE} 行，不得 UPDATE / DELETE / 软删原核销行。
 * 核销行的数据范围随 target（{@code RECEIVABLE → orderSellerScope}、
 * {@code PAYABLE → purchaserScope}，D-5）。
 */
@Service
@RequiredArgsConstructor
public class FinanceWriteOffService {

    private final FinanceWriteOffDao writeOffs;
    private final FinanceOperationLogRecorder operationLogs;
}
