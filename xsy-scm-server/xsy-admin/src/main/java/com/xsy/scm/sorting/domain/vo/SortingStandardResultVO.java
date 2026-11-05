package com.xsy.scm.sorting.domain.vo;

import java.util.List;

public record SortingStandardResultVO(Long taskId, List<Long> confirmedItemIds) {
}
