package com.xsy.scm.order.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderPastePreviewVO {
    private int totalRows;
    private int matchedRows;
    private int errorRows;
    private List<SalesOrderPastePreviewRowVO> rows = new ArrayList<>();
}
