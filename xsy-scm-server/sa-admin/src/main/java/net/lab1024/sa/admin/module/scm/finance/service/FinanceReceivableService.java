package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceivableDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceivableItemDao;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 应收域服务（F1-1 骨架，尚无业务方法）。
 *
 * <p><b>F1-2 在此实现</b>：签收 → 正常应收、退货批准 → 红字应收、以及「先退后签」时由签收补生成红字。
 * 金额口径见设计稿 §3.3 / §8.2 / §12。
 *
 * <p><b>F1-1 的硬边界</b>（越界即违反本轮范围）：
 * <ul>
 *   <li>本类**不得**被 {@code DeliveryRouteService.sign} 或 {@code OrderReturnService.approve} 调用 ——
 *       接触发点属 F1-2；</li>
 *   <li>**不得**实现任何金额上限校验：自动红字按批准额全额生成，不扣已核销额、不封顶、
 *       不静默丢差额（D-2 / D-4）。抛错会让退货批准事务整体回滚，等于财务反向控制订单域状态机；</li>
 *   <li>**不得**实现历史回填或补生成入口（D-1）；</li>
 *   <li>财务域对 {@code sales_order*} / {@code order_return*} / {@code inventory_*} /
 *       {@code delivery_*} <b>只读</b>（全局不变量 4），本包内不得出现针对这些表的
 *       INSERT / UPDATE / DELETE。</li>
 * </ul>
 *
 * <p>生成器不走幂等键：它不是用户命令，防重靠 {@code uk_finance_receivable*_source_active}，
 * 命中冲突即「已生成」并返回成功（设计稿 §11）。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableService {

    private final FinanceReceivableDao receivables;
    private final FinanceReceivableItemDao receivableItems;
    private final FinanceOperationLogRecorder operationLogs;
}
