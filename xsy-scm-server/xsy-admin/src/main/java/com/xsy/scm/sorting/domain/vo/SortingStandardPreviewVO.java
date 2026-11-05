package com.xsy.scm.sorting.domain.vo;

import java.util.List;

public record SortingStandardPreviewVO(Long taskId, Integer version, List<Item> items) {
    public record Item(Long id, Integer version, String orderNo, String customerName, String productName,
        String specName, String quantity, String unit, boolean eligible, String reason) {
    }
}
