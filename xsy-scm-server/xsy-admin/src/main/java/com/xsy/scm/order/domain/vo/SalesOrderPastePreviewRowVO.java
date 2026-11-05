package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 整单粘贴的一行预览结果。匹配失败的行也返回，前端在原行提示，不静默丢行。
 */
@Data
public class SalesOrderPastePreviewRowVO {
    /** 粘贴内容中的行号，从 1 开始。 */
    private int rowNumber;
    private String productName;
    private String specName;
    private String unit;
    private String quantity;
    private String rawUnitPrice;
    private String remark;

    private Long skuId;
    private String skuCode;
    private String matchedProductName;
    private String matchedSpecName;
    private String saleUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal resolvedUnitPrice;
    private String priceSource;
    /** 粘贴行显式给了单价，追加后按人工改价处理。 */
    private boolean manualPrice;
    private boolean matched;
    private List<SalesOrderImportErrorVO> errors = new ArrayList<>();
}
