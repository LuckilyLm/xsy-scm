package com.xsy.scm.pricing.constant;

public enum AgreementFormulaType {
    FIXED,
    MARKET_PLUS_AMOUNT,
    COST_MARKUP_RATE,
    MARKET_MARKUP_RATE,
    /**
     * 最近采购价 × (1 + 商品损耗率) × (1 + 商品税率) × (1 + 加价率)； 最近采购价缺失时回落到指定配送仓的当前单位成本。
     */
    PURCHASE_COST_LOSS_TAX_MARKUP
}
