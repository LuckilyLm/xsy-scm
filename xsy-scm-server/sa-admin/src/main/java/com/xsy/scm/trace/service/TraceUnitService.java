package com.xsy.scm.trace.service;

import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 包装码（一物一码）。
 *
 * <p>
 * 包装码是贴在被包装实物上的身份：一码一批、码由系统发号且不透明、永不复用；作废只追加事件，不改写原行。 发码总量受批次余额约束——包装码描述已经存在的实物，不能在库存之外凭空造出实物身份。
 */
@Service
@RequiredArgsConstructor
public class TraceUnitService {

    private static final char[] CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final TraceUnitDao traceUnitDao;

    private final ScmBusinessNoDao businessNoDao;

    /** 发码：同一批次一次发 N 个同规格包装，逐码追加一条发码事件。 */
    @Transactional(rollbackFor = Exception.class)
    public List<TraceUnitVO.Unit> issue(TraceUnitIssueForm form, String operator) {
        TraceUnitVO.Lot lot = traceUnitDao.findLot(form.getLotId());
        if (lot == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
        BigDecimal perPackage = parseQuantity(form.getQuantityPerPackage());
        String unit = traceUnitDao.lotUnit(form.getLotId());
        if (unit == null || unit.isBlank()) {
            // 批次还没有任何余额行：先有入库事实，才谈得上包装身份
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }
        BigDecimal adding = perPackage.multiply(BigDecimal.valueOf(form.getPackageCount()));
        BigDecimal balance = traceUnitDao.lotBalance(form.getLotId());
        BigDecimal issued = traceUnitDao.sumIssuedByLot(form.getLotId());
        if (issued.add(adding).compareTo(balance) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<TraceUnitVO.Unit> created = new ArrayList<>(form.getPackageCount());
        for (int index = 0; index < form.getPackageCount(); index++) {
            TraceUnitVO.Unit row = new TraceUnitVO.Unit();
            row.setUnitCode(nextCode());
            row.setLotId(form.getLotId());
            row.setPackageType(form.getPackageType());
            row.setQuantity(perPackage);
            row.setUnit(unit);
            row.setRemark(form.getRemark());
            row.setCreatedBy(operator);
            traceUnitDao.createUnit(row);
            traceUnitDao.appendIssueEvent(row.getId(), form.getLotId(), now, operator, form.getRemark());
            created.add(row);
        }
        return created;
    }

    /**
     * 分拣扫码绑定：把包装码绑到分拣任务行。
     *
     * <p>
     * 校验顺序即语义：码存在且有效 → 未绑过（一码一物）→ 商品规格一致 → 该行绑定总量不超过实拣量。 绑定是只追加事实，绑错只能作废该码，不能改绑到别的客户。
     */
    @Transactional(rollbackFor = Exception.class)
    public TraceUnitVO.Binding bind(TraceUnitBindForm form, String operator) {
        TraceUnitVO.Unit unit = traceUnitDao.findByCode(form.getUnitCode().trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        if (!"ACTIVE".equals(unit.getStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_VOID);
        }
        if (traceUnitDao.findBinding(unit.getId()) != null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_BOUND);
        }
        TraceUnitVO.SortingItem item = traceUnitDao.findSortingItem(form.getSortingTaskItemId());
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        TraceUnitVO.Lot lot = traceUnitDao.findLot(unit.getLotId());
        if (lot == null || !item.getSkuId().equals(lot.getSkuId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_SKU_MISMATCH);
        }
        BigDecimal bound = traceUnitDao.sumBoundBySortingItem(item.getItemId());
        if (bound.add(unit.getQuantity()).compareTo(item.getSortedQuantity()) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_BIND_EXCEED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        traceUnitDao.insertBinding(unit.getId(), unit.getLotId(), item.getTaskId(), item.getItemId(),
                item.getSalesOrderId(), item.getSalesOrderItemId(), item.getCustomerId(), item.getCustomerName(),
                item.getOrderNo(), now, operator);
        traceUnitDao.appendBindEvent(unit.getId(), unit.getLotId(), item.getItemId(), now, operator);

        TraceUnitVO.Binding binding = new TraceUnitVO.Binding();
        binding.setSortingTaskId(item.getTaskId());
        binding.setSortingTaskItemId(item.getItemId());
        binding.setOrderNo(item.getOrderNo());
        binding.setCustomerName(item.getCustomerName());
        binding.setBoundAt(now);
        binding.setBoundBy(operator);
        return binding;
    }

    @Transactional(readOnly = true)
    public PageResult<TraceUnitVO.Unit> query(TraceUnitQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        List<TraceUnitVO.Unit> list = traceUnitDao.queryPage(page, form);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /** 回放：单元 → 批次 → 收货来源 → 出库去向。找不到码直接报不存在，不返回半截结果。 */
    @Transactional(readOnly = true)
    public TraceUnitVO.Replay replay(String unitCode) {
        TraceUnitVO.Unit unit = traceUnitDao.findByCode(unitCode);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        TraceUnitVO.Replay replay = new TraceUnitVO.Replay();
        replay.setUnit(unit);
        replay.setLot(traceUnitDao.findLot(unit.getLotId()));
        replay.setReceipt(traceUnitDao.receiptOfLot(unit.getLotId()));
        replay.setBinding(traceUnitDao.findBinding(unit.getId()));
        replay.setOutbounds(traceUnitDao.outboundsOfLot(unit.getLotId()));
        return replay;
    }

    /**
     * 打印取数：单元 + 批次 + 绑定客户。
     *
     * <p>
     * 打印域只通过这里读包装码，数据范围与详情页同一套判定；找不到码按「不存在」回答。
     */
    @Transactional(readOnly = true)
    public TraceUnitVO.Replay printSource(Long unitId) {
        TraceUnitVO.Unit unit = traceUnitDao.findById(unitId);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        TraceUnitVO.Replay replay = new TraceUnitVO.Replay();
        replay.setUnit(unit);
        replay.setLot(traceUnitDao.findLot(unit.getLotId()));
        replay.setBinding(traceUnitDao.findBinding(unit.getId()));
        return replay;
    }

    /**
     * 不透明包装码：序号经 Base32 编码，前缀 U。
     *
     * <p>
     * 刻意不嵌日期、不嵌业务含义：码一旦贴上实物就不再变化，日期段会让「同一天发的码」看起来像同一批， 也会把业务信息泄露给扫码方。唯一性由序列保证，作废后原码不复用。
     */
    private String nextCode() {
        long sequence = businessNoDao.nextSequence("trace_unit_seq");
        char[] code = new char[9];
        code[0] = 'U';
        long value = sequence;
        for (int index = 8; index >= 1; index--) {
            code[index] = CODE_ALPHABET[(int) (value & 31)];
            value >>>= 5;
        }
        return new String(code);
    }

    private static BigDecimal parseQuantity(String text) {
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (RuntimeException error) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_QUANTITY_INVALID);
        }
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 4) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_QUANTITY_INVALID);
        }
        return value.setScale(4, java.math.RoundingMode.UNNECESSARY);
    }
}
