package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TraceLotDao;
import com.xsy.scm.trace.domain.vo.TraceLotVO;
import com.xsy.scm.trace.support.TraceLotContract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 批次档案的写入与读取。
 *
 * <p>
 * 内部批次号由 {@link ScmBusinessNoService} 按「前缀 + 北京时间业务日 + 全局序号」发号，不展示给前端； 来源生产批号只做 trim 与长度校验，原样保存且不参与唯一约束。批次档案创建后不改写。
 */
@Service
@RequiredArgsConstructor
public class TraceLotService implements TraceLotContract {

    private static final int SOURCE_CODE_MAX_LENGTH = 64;

    private static final String ORIGIN_PURCHASE_RECEIPT = "PURCHASE_RECEIPT";

    private final TraceLotDao traceLotDao;

    private final ScmBusinessNoService businessNoService;

    @Override
    public List<LotAllocation> createReceiptLots(CreateReceiptLots command) {
        if (command.drafts() == null || command.drafts().isEmpty()) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_REQUIRED);
        }
        List<LotAllocation> allocations = new ArrayList<>(command.drafts().size());
        for (LotDraft draft : command.drafts()) {
            String sourceLotNo = trimToNull(draft.sourceLotNo());
            if (sourceLotNo != null && sourceLotNo.length() > SOURCE_CODE_MAX_LENGTH) {
                throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_SOURCE_CODE_INVALID);
            }
            BigDecimal quantity = draft.quantity();
            if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4) {
                throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_QUANTITY_INVALID);
            }
            TraceLotVO.Lot lot = new TraceLotVO.Lot();
            lot.setLotNo(businessNoService.nextDated(ScmBusinessNoType.TRACE_LOT));
            lot.setSourceLotNo(sourceLotNo);
            lot.setSupplierId(command.supplierId());
            lot.setSkuId(command.skuId());
            lot.setOriginType(ORIGIN_PURCHASE_RECEIPT);
            lot.setFirstReceiptItemId(command.receiptItemId());
            lot.setCreatedBy(command.operator());
            traceLotDao.createLot(lot);
            traceLotDao.linkReceiptItemLot(command.receiptItemId(), lot.getId(), quantity, command.operator());
            allocations.add(new LotAllocation(lot.getId(), lot.getLotNo(), sourceLotNo, quantity));
        }
        return allocations;
    }

    @Override
    public List<TraceLotVO.ReceiptItemLot> listByReceiptItemIds(List<Long> receiptItemIds) {
        if (receiptItemIds == null || receiptItemIds.isEmpty()) {
            return List.of();
        }
        return traceLotDao.listByReceiptItemIds(receiptItemIds);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
