package com.xsy.scm.balance.support;

import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.vo.BalanceMovementVO;

/**
 * 实体 → VO 的转换（纯函数）。
 *
 * <p>
 * 单独放一处而不是让 Controller 各拼各的：VO 是<b>对外契约</b>，拼装散在多个入口里， 迟早出现「列表接口有金额、更正接口漏了金额」这种不一致。
 */
public final class BalanceVoAssembler {

    private BalanceVoAssembler() {
    }

    public static BalanceMovementVO toMovement(CustomerBalanceMovementEntity row) {
        if (row == null) {
            return null;
        }
        BalanceMovementVO vo = new BalanceMovementVO();
        vo.setId(row.getId());
        vo.setMovementNo(row.getMovementNo());
        vo.setAccountId(row.getAccountId());
        vo.setSettlementCustomerId(row.getSettlementCustomerId());
        vo.setCustomerId(row.getCustomerId());
        vo.setMovementType(row.getType());
        vo.setDirection(row.getDirection());
        vo.setAmount(row.getAmount());
        vo.setSourceType(row.getSourceType());
        vo.setSourceId(row.getSourceId());
        vo.setReason(row.getReason());
        vo.setOccurredAt(row.getOccurredAt());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setOperator(row.getCreatedBy());
        return vo;
    }
}
