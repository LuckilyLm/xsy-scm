package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据大屏-趋势数据（近 7 / 30 天）。
 *
 * <p><b>为什么一次返回 8 条序列而不是按图拆接口</b>：底部趋势带要同时画销售、采购、库存三张图，
 * 按图拆会让大屏为了三张图调用十几个接口，且三张图的日期轴必须完全对齐 ——
 * 一次返回能保证这一点，也避免各接口的时间区间出现毫秒级偏差。
 *
 * <p><b>日期口径是 Asia/Shanghai，不是 UTC</b>：业务上的「今天」是北京时间的今天。
 * 用 UTC 日界会把窗口整体平移 8 小时（实际变成 08:00 → 次日 08:00），
 * 早上下的单会被算到前一天。这一条与需求日期 {@code demand_date} 的口径一致。
 *
 * <p>{@code inventoryQuantity} 是**每日期末库存量**（当日及之前所有流水的净额累加），
 * 不是当日变动量；当日变动量看 {@code inboundQuantity} / {@code outboundQuantity}。
 */
@Data
@Schema(description = "数据大屏-趋势数据")
public class ScreenTrendVO {

    @Schema(description = "区间标识（7d / 30d）")
    private String range;

    @Schema(description = "日期轴（MM-DD）")
    private List<String> dates;

    @Schema(description = "日期轴（YYYY-MM-DD，用于 tooltip 完整展示）")
    private List<String> fullDates;

    @Schema(description = "每日结算金额（CONFIRMED）")
    private List<BigDecimal> sales;

    @Schema(description = "每日订单数（CONFIRMED）")
    private List<Long> orders;

    @Schema(description = "每日采购金额")
    private List<BigDecimal> purchaseAmounts;

    @Schema(description = "每日采购单数")
    private List<Long> purchaseOrders;

    @Schema(description = "每日期末库存量（累计净额，非当日变动）")
    private List<BigDecimal> inventoryQuantity;

    @Schema(description = "每日入库数量（五个入方向流水求和）")
    private List<BigDecimal> inboundQuantity;

    @Schema(description = "每日出库数量（五个出方向流水求和）")
    private List<BigDecimal> outboundQuantity;

    /**
     * 单日聚合行（SQL 直接映射，再由服务层转置成上面的数组）。
     *
     * <p>SQL 用「一天一行、八个标量子查询」而不是「八个查询分别 group by」：
     * 日期轴由 {@code generate_series} 生成，**没有任何单据的日期也必须出现在轴上**，
     * 否则前端折线会在缺数据的日期上断开或错位。
     */
    @Data
    @Schema(description = "趋势单日聚合行")
    public static class Point {
        private String date;
        private String label;
        private Long orders;
        private BigDecimal sales;
        private Long purchaseOrders;
        private BigDecimal purchaseAmounts;
        private BigDecimal inventoryQuantity;
        private BigDecimal inboundQuantity;
        private BigDecimal outboundQuantity;
    }
}
