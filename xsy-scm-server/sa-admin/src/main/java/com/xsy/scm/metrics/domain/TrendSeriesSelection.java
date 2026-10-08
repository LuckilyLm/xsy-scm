package com.xsy.scm.metrics.domain;

/** 库存期末累计只供完整经营趋势使用；库存进出趋势无需扫描期初历史。 */
public record TrendSeriesSelection(boolean sales, boolean purchase, boolean inventory, boolean closingInventory) {

    public static final TrendSeriesSelection ALL = new TrendSeriesSelection(true, true, true, true);
    public static final TrendSeriesSelection SALES = new TrendSeriesSelection(true, false, false, false);
    public static final TrendSeriesSelection PURCHASE = new TrendSeriesSelection(false, true, false, false);
    public static final TrendSeriesSelection INVENTORY = new TrendSeriesSelection(false, false, true, false);
}
