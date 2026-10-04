package com.xsy.scm.inventory.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import com.xsy.scm.inventory.dao.InventoryWarningStateDao;
import com.xsy.scm.inventory.domain.entity.InventoryWarningStateEntity;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.notification.service.ScmNotificationService;
import com.xsy.scm.notification.constant.ScmNotificationEventTypeEnum;
import com.xsy.scm.warehouse.dao.EmployeeWarehouseScopeDao;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.module.support.message.constant.MessageTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存预警阈值通知的单行评估与投递。
 *
 * <p>
 * <b>跃迁语义（ADR-007）</b>：只在「{@code NORMAL → LOW/HIGH}」时投递，异常持续期间不重复， 回到 {@code NORMAL} 之后再次异常可以再提醒一次。判定「上次是什么状态」靠
 * {@code scm_inventory_warning_state}，而状态本身仍由 {@link ScmInventoryWarningStatusEnum#evaluate} 现算 —— 这里不复制一份阈值规则。
 *
 * <p>
 * 每次跃迁的 {@code event_key} 由 {@code (仓库, SKU, 纪元, 状态, 接收人)} 组成：
 * <ul>
 * <li>带接收人 —— 一个仓可能有多名仓管，键不含接收人会让唯一约束把第二个人之后全部挡掉；</li>
 * <li>带纪元 —— 「恢复后再次异常」必须落在一个新的键上，否则会被当成重复事件丢掉。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarningNotifier {

    private static final String EVENT_TYPE = ScmNotificationEventTypeEnum.INVENTORY_WARNING.name();

    private final InventoryWarningStateDao stateDao;

    private final EmployeeWarehouseScopeDao employeeWarehouseScopeDao;

    private final ScmNotificationService notificationService;

    /**
     * 评估一行阈值配置，必要时投递通知。
     *
     * @return 本次实际投递的通知条数（0 = 没有跃迁、无接收人，或该键已投递过）
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluateAndNotify(InventoryWarningVO row) {
        ScmInventoryWarningStatusEnum current = ScmInventoryWarningStatusEnum.evaluate(row.getAvailableQuantity(),
                row.getWarnMin(), row.getWarnMax());
        Long warehouseId = row.getWarehouseId();
        Long skuId = row.getSkuId();
        String operator = operator();

        InventoryWarningStateEntity state = stateDao.selectForUpdate(warehouseId, skuId);
        if (state == null) {
            if (stateDao.insertIfAbsent(warehouseId, skuId, current.name(), 0, operator) == 1) {
                // 首次观察：没有历史状态可比较，异常就提醒一次（「设了下限却一件没有」正是这种情形）
                return current.isAbnormal() ? notify(row, current, 0) : 0;
            }
            // 并发对手先写了这一行：重读后按普通跃迁处理
            state = stateDao.selectForUpdate(warehouseId, skuId);
            if (state == null) {
                return 0;
            }
        }

        if (current.name().equals(state.getStatus())) {
            // 状态没变：异常持续期间不重复提醒，也不写库
            return 0;
        }
        int epoch = state.getEpoch() == null ? 0 : state.getEpoch();
        if (!current.isAbnormal()) {
            // 回到正常才推进纪元，下一次异常于是拿到一个新的 event_key
            epoch = epoch + 1;
        }
        stateDao.updateState(warehouseId, skuId, current.name(), epoch, operator);
        return current.isAbnormal() ? notify(row, current, epoch) : 0;
    }

    /**
     * 向该仓的授权员工投递站内信。
     *
     * <p>
     * 接收人来自 {@code employee_warehouse_scope}：授权行是「谁负责这个仓」的唯一正式来源。 没有授权行时<b>不广播、不猜人</b>，只记一条日志 —— 把预警发给全公司比不发更糟。
     */
    private int notify(InventoryWarningVO row, ScmInventoryWarningStatusEnum status, int epoch) {
        List<Long> receivers = employeeWarehouseScopeDao.listEnabledEmployeeIdsByWarehouse(row.getWarehouseId());
        if (receivers == null || receivers.isEmpty()) {
            log.info("库存预警无接收人，跳过通知 warehouseId={} skuId={} status={}", row.getWarehouseId(), row.getSkuId(),
                    status.name());
            return 0;
        }
        String title = status == ScmInventoryWarningStatusEnum.LOW ? "库存低于下限" : "库存高于上限";
        String content = content(row, status);
        int sent = 0;
        for (Long receiverUserId : receivers) {
            String eventKey = EVENT_TYPE + ":" + row.getWarehouseId() + ":" + row.getSkuId() + ":" + epoch + ":"
                    + status.name() + ":" + receiverUserId;
            if (notificationService.sendOnce(eventKey, EVENT_TYPE, MessageTypeEnum.SCM_INVENTORY_WARNING.getValue(),
                    receiverUserId, row.getThresholdId(), title, content)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * 通知正文。
     *
     * <p>
     * 只写可用量、阈值与仓库 / SKU 标识，<b>不带金额与成本</b>：接收人来自仓库授权行， 其中包含默认看不到采购价与均价仓管角色，通知正文不能成为绕过金额权限的旁路。
     */
    private static String content(InventoryWarningVO row, ScmInventoryWarningStatusEnum status) {
        StringBuilder text = new StringBuilder();
        text.append("仓库 ").append(text(row.getWarehouseName(), row.getWarehouseId()));
        text.append(" 的 ").append(text(row.getSkuCode(), row.getSkuId()));
        if (row.getProductName() != null && !row.getProductName().isBlank()) {
            text.append("（").append(row.getProductName()).append("）");
        }
        text.append(" 可用量 ").append(amount(row.getAvailableQuantity()));
        if (row.getUnit() != null && !row.getUnit().isBlank()) {
            text.append(' ').append(row.getUnit());
        }
        if (status == ScmInventoryWarningStatusEnum.LOW) {
            text.append(" 低于下限 ").append(amount(row.getWarnMin())).append("，请安排补货。");
        } else {
            text.append(" 高于上限 ").append(amount(row.getWarnMax())).append("，请检查是否积压。");
        }
        return text.toString();
    }

    private static String text(String value, Long fallbackId) {
        return value == null || value.isBlank() ? String.valueOf(fallbackId) : value;
    }

    private static String amount(BigDecimal value) {
        // 阈值可以为空（只设一个方向），数量被 SQL COALESCE 成 0 所以不会为空；
        // 真取到空值时给「—」而不是「0」——「没有阈值」和「阈值是 0」是两件事。
        return value == null ? "—" : value.toPlainString();
    }

    /** 定时任务没有登录上下文，用 system 标记去重行的来源；业务写路径不受影响。 */
    private static String operator() {
        String operator = ScmOperator.currentOrNull();
        return operator == null ? ScmNotificationService.SYSTEM_OPERATOR : operator;
    }
}
