package com.xsy.scm.promotion.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 优惠金额按订单明细分配，分配后的合计与订单优惠总额一致；尾差按固定规则处理。
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
        BigDecimal baseTotal = safeLines.stream().map(line -> amount(line.baseAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = clamp(amount(requestedDiscount), baseTotal);

        if (safeLines.isEmpty() || discount.signum() == 0) {
            List<Allocation> zero = new ArrayList<>(safeLines.size());
            safeLines.forEach(line -> zero
                .add(new Allocation(line.orderItemId(), amount(line.baseAmount()), BigDecimal.ZERO.setScale(SCALE))));
            return new Result(baseTotal, discount, zero, null);
        }

        // 第一轮：按比例取整
        List<Allocation> allocations = new ArrayList<>(safeLines.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (Line line : safeLines) {
            BigDecimal base = amount(line.baseAmount());
            BigDecimal share = baseTotal.signum() == 0
                ? BigDecimal.ZERO
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
                BigDecimal applied = residual.abs().min(room)
                    .multiply(residual.signum() >= 0 ? BigDecimal.ONE : BigDecimal.ONE.negate());
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
     * 不用入参顺序或并列时随意处理：那样同一份订单两次计算会把差额放到不同行，而分摊要保存进订单。
     */
    private static List<Integer> targetOrder(List<Allocation> allocations) {
        List<Integer> order = new ArrayList<>(allocations.size());
        for (int index = 0; index < allocations.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparing((Integer index) -> allocations.get(index).baseAmount()).reversed()
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
