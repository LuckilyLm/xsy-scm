package com.xsy.scm.promotion.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 订单优惠的**按行比例分摊**（纯函数）。
 *
 * <p>
 * 规则来自 ADR-009：优惠按订单行金额比例分摊并冻结；舍入差额以**确定性规则**归集。
 * 「确定性」在这里是可复现的意思 —— 同样的输入永远得到同样的分摊，不依赖集合顺序或时钟。
 *
 * <p>
 * 三个必须做对的地方：
 * <ol>
 * <li><b>比例分摊后逐行取整到 4 位</b>（{@code HALF_UP}，与全仓金额口径一致），
 * 因此各行之和通常不等于总优惠，差额必须归集而不是丢掉；</li>
 * <li><b>归集目标固定</b>：基础金额最大的一行，同额时取行 id 最小的一行。归集后若该行被
 * 顶到超过自己的基础金额，则顺延到下一行 —— 允许「某行优惠超过行金额」会直接造出负数行金额；</li>
 * <li><b>优惠总额先夹在 [0, 基础合计]</b>：活动算出来的减免可能超过订单金额（例如门槛满足但
 * 客户只买了很少的量），越界的分摊没有意义。</li>
 * </ol>
 */
public final class PromotionDiscountAllocator {

    private static final int SCALE = 4;

    private PromotionDiscountAllocator() {
    }

    /**
     * 参与分摊的一行。
     *
     * @param orderItemId
     *            订单行 id；只用于标识与稳定排序
     * @param baseAmount
     *            该行的基础金额（活动价之前的价格 × 数量），必须非负
     */
    public record Line(Long orderItemId, BigDecimal baseAmount) {
    }

    /**
     * 分摊结果的一行。
     */
    public record Allocation(Long orderItemId, BigDecimal baseAmount, BigDecimal discountAmount) {
    }

    /**
     * 分摊结果。
     *
     * @param baseAmount
     *            基础合计
     * @param discountAmount
     *            实际优惠合计（已夹到 {@code [0, baseAmount]}）
     * @param allocations
     *            逐行分摊，顺序与入参一致
     * @param roundingTargetItemId
     *            承接舍入差额的行；没有差额时为 {@code null}
     */
    public record Result(BigDecimal baseAmount, BigDecimal discountAmount, List<Allocation> allocations,
            Long roundingTargetItemId) {
    }

    /**
     * 按行金额比例分摊优惠。
     */
    public static Result allocate(List<Line> lines, BigDecimal requestedDiscount) {
        List<Line> safeLines = lines == null ? List.of() : lines;
        BigDecimal baseTotal = safeLines.stream().map(PromotionDiscountAllocator::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = clamp(amount(requestedDiscount), baseTotal);

        if (safeLines.isEmpty() || discount.signum() == 0) {
            List<Allocation> zero = new ArrayList<>(safeLines.size());
            safeLines.forEach(line -> zero.add(new Allocation(line.orderItemId(), amount(line.baseAmount()),
                    BigDecimal.ZERO.setScale(SCALE))));
            return new Result(baseTotal, discount, zero, null);
        }

        // 第一轮：按比例取整
        List<Allocation> allocations = new ArrayList<>(safeLines.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (Line line : safeLines) {
            BigDecimal base = amount(line.baseAmount());
            BigDecimal share = baseTotal.signum() == 0 ? BigDecimal.ZERO
                    : discount.multiply(base).divide(baseTotal, SCALE, RoundingMode.HALF_UP);
            allocations.add(new Allocation(line.orderItemId(), base, share));
            allocated = allocated.add(share);
        }

        // 第二轮：把差额归集到确定的目标行（按基础金额降序、行 id 升序）
        BigDecimal residual = discount.subtract(allocated);
        Long target = null;
        if (residual.signum() != 0) {
            List<Integer> order = targetOrder(allocations);
            for (Integer index : order) {
                if (residual.signum() == 0) {
                    break;
                }
                Allocation current = allocations.get(index);
                BigDecimal room = current.baseAmount().subtract(current.discountAmount());
                if (room.signum() <= 0) {
                    continue;
                }
                BigDecimal applied = residual.abs().min(room).multiply(residual.signum() >= 0
                        ? BigDecimal.ONE
                        : BigDecimal.ONE.negate());
                allocations.set(index, new Allocation(current.orderItemId(), current.baseAmount(),
                        current.discountAmount().add(applied).setScale(SCALE, RoundingMode.HALF_UP)));
                residual = residual.subtract(applied);
                if (target == null) {
                    target = current.orderItemId();
                }
            }
        }

        BigDecimal finalTotal = allocations.stream().map(Allocation::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        return new Result(baseTotal, finalTotal, allocations, target);
    }

    /**
     * 归集顺序：基础金额降序、同额按行 id 升序。
     *
     * <p>
     * 不用「入参顺序」或「金额最大但并列时随意」：那会让同一份订单在两次计算中把差额放到
     * 不同行上，而分摊是要冻结进订单的。
     */
    private static List<Integer> targetOrder(List<Allocation> allocations) {
        List<Integer> order = new ArrayList<>(allocations.size());
        for (int index = 0; index < allocations.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator
                .comparing((Integer index) -> allocations.get(index).baseAmount()).reversed()
                .thenComparing(index -> allocations.get(index).orderItemId()));
        return order;
    }

    private static BigDecimal clamp(BigDecimal discount, BigDecimal baseTotal) {
        if (discount.signum() < 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return discount.compareTo(baseTotal) > 0 ? baseTotal : discount;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
