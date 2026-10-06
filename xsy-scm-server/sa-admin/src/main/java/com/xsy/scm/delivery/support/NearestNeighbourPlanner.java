package com.xsy.scm.delivery.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 最近邻排线规则（从起点出发，每步去最近的未访问点）。
 *
 * <p>
 * <b>它是一个可解释的启发式，不是最优解</b>：把规则代码、每一段的距离和累计距离一起冻结进 建议，操作者才能回答「为什么这单排在前面」。不承诺最短路径 —— 那需要真正的路径优化， 属于后续能力，这里不冒名。
 *
 * <p>
 * 纯函数，无状态、无外部依赖，便于直接单测。
 */
public final class NearestNeighbourPlanner {

    /**
     * 规则代码；冻结进建议，界面按它展示「按什么规则排的」。
     */
    public static final String RULE_CODE = "NEAREST_NEIGHBOUR_FROM_START";

    /** 距离保留 4 位小数（米），与距离矩阵同一口径。 */
    private static final int SCALE = 4;

    private NearestNeighbourPlanner() {
    }

    /**
     * @param order
     *            访问顺序（索引），首元素恒为 0（起点），其余为停靠点在入参列表中的下标
     * @param legDistances
     *            与 {@code order} 对齐的<b>每段</b>距离；第 0 段为 0（起点自身）
     * @param cumulativeDistances
     *            与 {@code order} 对齐的累计距离
     * @param totalDistance
     *            总距离 = 最后一段的累计距离
     */
    public record Plan(List<Integer> order, List<BigDecimal> legDistances, List<BigDecimal> cumulativeDistances,
            BigDecimal totalDistance) {
    }

    /**
     * 计算访问顺序。矩阵必须是方阵且对称（由 provider 契约保证）。
     *
     * @param matrix
     *            距离矩阵；索引 0 约定为起点
     */
    public static Plan plan(BigDecimal[][] matrix) {
        int size = matrix.length;
        List<Integer> order = new ArrayList<>(size);
        List<BigDecimal> legs = new ArrayList<>(size);
        List<BigDecimal> cumulative = new ArrayList<>(size);
        boolean[] visited = new boolean[size];
        BigDecimal zero = BigDecimal.ZERO.setScale(SCALE);
        order.add(0);
        legs.add(zero);
        cumulative.add(zero);
        visited[0] = true;
        int current = 0;
        for (int step = 1; step < size; step++) {
            int next = -1;
            BigDecimal best = null;
            for (int candidate = 0; candidate < size; candidate++) {
                if (visited[candidate]) {
                    continue;
                }
                BigDecimal distance = matrix[current][candidate];
                if (best == null || distance.compareTo(best) < 0) {
                    best = distance;
                    next = candidate;
                }
            }
            visited[next] = true;
            order.add(next);
            legs.add(best);
            cumulative.add(cumulative.get(cumulative.size() - 1).add(best));
            current = next;
        }
        return new Plan(order, legs, cumulative, cumulative.get(cumulative.size() - 1));
    }
}
