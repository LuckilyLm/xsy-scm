package com.xsy.scm.sorting.service;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.sorting.constant.ScmSortingOccupationStatusEnum;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.vo.SortingPartRecordVO;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SortingStandardPolicy {
    public String rejection(SortingTaskItemEntity row, SortingPartRecordVO previous) {
        if (!ScmSortingOccupationStatusEnum.ACTIVE.name().equals(row.getOccupationStatus())) {
            return "明细已释放";
        }
        if (!ScmProductTypeEnum.STANDARD.name().equals(row.getProductTypeSnapshot())) {
            return "仅支持标准品";
        }
        if (row.getResult() != null || row.getSortedQuantity() != null) {
            return "已确认明细，请通过人工录入更正";
        }
        BigDecimal quantity = row.getPlannedQuantitySnapshot();
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 4
            || quantity.compareTo(new BigDecimal("99999999999999.9999")) > 0) {
            return "计划数量无效，请人工核对";
        }
        if (StringUtils.isBlank(row.getSaleUnitSnapshot())) {
            return "缺少销售单位，请人工核对";
        }
        if (previous != null
            && (previous.getTotalQuantity() == null || previous.getTotalQuantity().compareTo(quantity) != 0)) {
            return "分次累计量与计划量不同，请人工确认并填写原因";
        }
        return null;
    }
}
