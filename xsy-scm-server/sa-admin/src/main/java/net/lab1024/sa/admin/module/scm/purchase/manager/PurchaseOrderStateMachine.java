package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import java.util.Set;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;

/**
 * 采购单状态机（6 状态，W5 Target Design §4.1 / §4.2）。
 *
 * <p>实现为**声明式策略**，不是散落的 if。转换表与 V15 的 `ck_purchase_order_status` 白名单、
 * 以及 §4.2 的 T1–T8 逐条对应。
 *
 * <pre>
 * DRAFT ──submit──→ SUBMITTED ──收货确认──→ PARTIALLY_RECEIVED ──收货确认──→ RECEIVED
 *   │                    │                          │
 *   │cancel              │cancel                    │short-close
 *   ▼                    ▼                          ▼
 * CANCELLED ←────────────┘                     SHORT_CLOSED
 * </pre>
 *
 * <p><b>P14</b>：{@code PARTIALLY_RECEIVED} **不允许 cancel**；需要终止时用 {@link #shortClosable}
 * 对应的 {@code shortClose}（Q2a）。
 * <p>{@code RECEIVED} / {@code SHORT_CLOSED} / {@code CANCELLED} 是**终态**。
 */
public final class PurchaseOrderStateMachine {

    private PurchaseOrderStateMachine() {
    }

    /** 状态图：只有表里列出的 (from, to) 是合法的。 */
    public static boolean canTransition(String from, String to) {
        if (to == null) {
            // Set.of(...).contains(null) 会抛 NPE（ImmutableCollections 拒绝 null 查询），
            // 因此必须在这里显式短路：null 目标状态是「非法」，不是「程序错误」。
            return false;
        }
        return switch (from == null ? "" : from) {
            case "DRAFT" -> Set.of("SUBMITTED", "CANCELLED").contains(to);
            case "SUBMITTED" -> Set.of("PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED").contains(to);
            // 同一状态到自身是合法的：第二次收货后仍是 PARTIALLY_RECEIVED
            case "PARTIALLY_RECEIVED" -> Set.of("PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED").contains(to);
            default -> false;   // RECEIVED / SHORT_CLOSED / CANCELLED 为终态
        };
    }

    /** 断言转换合法，否则 40982。 */
    public static void transition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }
    }

    /** 只有 {@code DRAFT} 可编辑行与需求分配（T2）。 */
    public static boolean editable(String status) {
        return "DRAFT".equals(status);
    }

    /** 可建 / 可确认收货：{@code SUBMITTED} 或 {@code PARTIALLY_RECEIVED}（T7/T8）。 */
    public static boolean receivable(String status) {
        return "SUBMITTED".equals(status) || "PARTIALLY_RECEIVED".equals(status);
    }

    /** 可取消：{@code DRAFT} 或 {@code SUBMITTED}（T4）。**不含** {@code PARTIALLY_RECEIVED}（P14）。 */
    public static boolean cancellable(String status) {
        return "DRAFT".equals(status) || "SUBMITTED".equals(status);
    }

    /** 可少收关单：仅 {@code PARTIALLY_RECEIVED}（T5）。 */
    public static boolean shortClosable(String status) {
        return "PARTIALLY_RECEIVED".equals(status);
    }

    /** 是否终态（只读）。 */
    public static boolean terminal(String status) {
        return "RECEIVED".equals(status) || "SHORT_CLOSED".equals(status) || "CANCELLED".equals(status);
    }

    /**
     * 收货确认后由「全部行是否收齐」推导出的采购单新状态（T7）。
     *
     * <p>注意：**已收满的采购单不再回到 {@code PARTIALLY_RECEIVED}**；只要所有活动行
     * {@code received >= planned} 就是 {@code RECEIVED}，否则 {@code PARTIALLY_RECEIVED}。
     */
    public static String afterReceipt(boolean allLinesFulfilled) {
        return allLinesFulfilled ? "RECEIVED" : "PARTIALLY_RECEIVED";
    }
}
