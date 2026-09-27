package com.xsy.scm.finance.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 核销域服务（骨架，尚无业务方法）。
 *
 * <p><b> 在此实现</b>：M:N 核销（{@code scm:finance:write-off:add}）与反向核销
 * （{@code scm:finance:write-off:reverse}）。
 *
 * <p>核销校验全部在**持有目标锁之后**做：
 * ① source 与 target 的结算对方必须一致，否则 {@code 41136}；
 * ② 本次金额 ≤ 目标 {@code openAmount}，否则 {@code 41135}；
 * ③ 本次金额 ≤ source 待核销余额，否则同上。
 * {@code openAmount = max(netAmount − writtenOffAmount, 0)}，因此 下净应收为负的单据
 * {@code openAmount = 0}、自然不可再被核销，不需要为负数另设分支。
 *
 * <p><b> 的硬边界</b>：不得把已核销额 / 结清状态写回任何表（全局不变量 6）；
 * 撤销只能新增 {@code REVERSE} 行，不得 UPDATE / DELETE / 软删原核销行。
 * 核销行的数据范围随 target（{@code RECEIVABLE → orderSellerScope}、
 * {@code PAYABLE → purchaserScope}）。
 */
@Service
@RequiredArgsConstructor
public class FinanceWriteOffService {

    private final FinanceWriteOffDao financeWriteOffDao;
    private final FinanceOperationLogRecorder operationLogs;
}
