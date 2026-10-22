package com.xsy.scm.trace.service;

import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.inventory.service.InventorySaleUnitConverter;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import com.xsy.scm.trace.constant.ScmTraceUnitStatusEnum;
import com.xsy.scm.trace.constant.ScmTraceWeightSourceEnum;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceScaleUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.form.TraceUnitVoidForm;
import com.xsy.scm.trace.domain.form.TraceUnitWeightForm;
import com.xsy.scm.trace.domain.vo.TraceScaleUnitVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.support.TracePublicLinkBuilder;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 包装码（一物一码）。
 *
 * <p>
 * 包装码是贴在被包装实物上的身份：一码一批、码由系统发号且不透明、永不复用；作废只追加事件，不改写原行。 发码总量受批次余额约束——包装码描述已经存在的实物，不能在库存之外凭空造出实物身份。
 *
 * <p>
 * 三条硬约束：所有入口都先过仓库数据范围（批次归属仓库由首次收货单决定）；发码先锁批次余额行再算可发量； 绑定与作废先锁同一个包装码行，避免「作废后仍被绑定」的交叉状态。
 */
@Service
@RequiredArgsConstructor
public class TraceUnitService {

    private static final char[] CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 公开令牌长度：16 位 32 进制约 80 bit，标签上放得下，也扛得住按 token 枚举。 */
    private static final int PUBLIC_TOKEN_LENGTH = 16;

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final TraceUnitDao traceUnitDao;

    private final ScmBusinessNoDao businessNoDao;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    private final ScmDataScopeService dataScopeService;

    private final TracePublicLinkBuilder tracePublicLinkBuilder;

    /**
     * 发码：同一批次一次发 N 个同规格包装，逐码追加一条发码事件。
     *
     * <p>
     * 先锁批次余额行再检查发码额度，序列化同批次发码和库存出库。 在建立包装码级出库事实前，批次级出库不能证明某一已发码实物离场； 因此累计发码量（含作废码）不扣批次出库，保守地与当前批次余额比较。
     * 出库后无法自动释放额度，必须等未来单元级出库/退回关联落地，不能靠猜测复用包装身份。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<TraceUnitVO.Unit> issue(TraceUnitIssueForm form, String operator) {
        TraceUnitVO.Lot lot = traceUnitDao.findLot(form.getLotId());
        if (lot == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(form.getLotId()));
        BigDecimal perPackage = parseQuantity(form.getQuantityPerPackage());
        String unit = traceUnitDao.lotUnit(form.getLotId());
        if (unit == null || unit.isBlank()) {
            // 批次还没有任何余额行：先有入库事实，才谈得上包装身份
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }
        BigDecimal balance = lockedBalance(form.getLotId());
        BigDecimal adding = perPackage.multiply(BigDecimal.valueOf(form.getPackageCount()));
        BigDecimal issued = traceUnitDao.sumIssuedByLot(form.getLotId());
        if (issued.add(adding).compareTo(balance) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<TraceUnitVO.Unit> created = new ArrayList<>(form.getPackageCount());
        for (int index = 0; index < form.getPackageCount(); index++) {
            TraceUnitVO.Unit row = new TraceUnitVO.Unit();
            row.setUnitCode(nextCode());
            row.setPublicToken(nextPublicToken());
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
     * 先锁包装码行、再锁分拣行，两步都在同一事务内：前者让绑定与作废串行化，后者让同一分拣行上的并发绑定串行化， 否则两枚码各自读到相同的剩余可绑定量、合计超出实拣量。校验顺序即语义：码存在且有效 → 未绑过（一码一物） →
     * 分拣任务可绑定 → 商品规格一致 → 仓库一致 → 该行绑定总量不超过实拣量。
     */
    @Transactional(rollbackFor = Exception.class)
    public TraceUnitVO.Binding bind(TraceUnitBindForm form, String operator) {
        TraceUnitVO.Unit unit = traceUnitDao.lockByCode(form.getUnitCode().trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        if (!ScmTraceUnitStatusEnum.ACTIVE.name().equals(unit.getStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_VOID);
        }
        if (traceUnitDao.findBinding(unit.getId()) != null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_BOUND);
        }
        TraceUnitVO.SortingItem item = traceUnitDao.lockSortingItem(form.getSortingTaskItemId());
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        // 已取消的任务不能再承接实物；未开始的任务也还没有实拣量
        if (!ScmSortingTaskStatusEnum.SORTING.name().equals(item.getTaskStatus())
                && !ScmSortingTaskStatusEnum.COMPLETED.name().equals(item.getTaskStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_TASK_STATE_INVALID);
        }
        warehouseScopeGuard.require(item.getWarehouseId());
        if (traceUnitDao.countConfirmedOutboundBySortingItem(item.getItemId()) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_OUTBOUND);
        }
        TraceUnitVO.Lot lot = traceUnitDao.findLot(unit.getLotId());
        if (lot == null || !item.getSkuId().equals(lot.getSkuId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_SKU_MISMATCH);
        }
        // 商品对得上还不够：这一袋必须来自本任务所在仓库，否则「批次级召回」会被跨仓数据污染
        Long lotWarehouseId = traceUnitDao.lotWarehouseId(unit.getLotId());
        if (lotWarehouseId == null || !lotWarehouseId.equals(item.getWarehouseId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WAREHOUSE_MISMATCH);
        }
        BigDecimal bound = traceUnitDao.sumBoundBySortingItem(item.getItemId());
        if (bound.add(unit.getQuantity())
                .compareTo(saleToStockQuantity(item, item.getSortedQuantity(), unit.getUnit())) > 0) {
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

    /**
     * 作废：只对未绑定的码生效。
     *
     * <p>
     * 与绑定共用同一把包装码行锁：否则「绑定读到 ACTIVE」与「作废读到无绑定」可以同时通过， 留下「已作废的码挂着绑定」这种谁也解释不了的状态。数据库侧另有触发器兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidUnit(TraceUnitVoidForm form, String operator) {
        TraceUnitVO.Unit unit = traceUnitDao.lockByCode(form.getUnitCode().trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
        if (!ScmTraceUnitStatusEnum.ACTIVE.name().equals(unit.getStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_VOID);
        }
        if (traceUnitDao.findBinding(unit.getId()) != null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_VOID_BOUND);
        }
        if (traceUnitDao.markVoid(unit.getId()) != 1) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_VOID);
        }
        traceUnitDao.appendVoidEvent(unit.getId(), unit.getLotId(), OffsetDateTime.now(), operator,
                form.getReason().trim());
    }

    /**
     * 净重补录：只对还没有重量的码生效。
     *
     * <p>
     * 与作废共用同一把包装码行锁：否则两个补录请求会各自读到「还没有重量」，后写入的一方静默覆盖前一方。 已经称过的码要走称重更正，不能在这里改数——原始结论一旦可覆盖，标签上印的净重就没有意义。
     */
    @Transactional(rollbackFor = Exception.class)
    public TraceUnitVO.Unit weigh(String unitCode, TraceUnitWeightForm form, String operator) {
        TraceUnitVO.Unit unit = traceUnitDao.lockByCode(unitCode.trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
        if (unit.getNetWeight() != null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WEIGHT_ALREADY_SET);
        }
        BigDecimal weight = parseWeight(form.getNetWeight());
        OffsetDateTime now = OffsetDateTime.now();
        if (traceUnitDao.updateWeight(unit.getId(), weight, form.getWeightUnit().trim(),
                ScmTraceWeightSourceEnum.MANUAL.name(), now) != 1) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WEIGHT_ALREADY_SET);
        }
        traceUnitDao.appendWeighEvent(unit.getId(), unit.getLotId(), now, operator, form.getReason().trim());
        return traceUnitDao.findById(unit.getId());
    }

    /**
     * 秤端按筐发码：一筐一次称重对应一枚包装码。
     *
     * <p>
     * 以称重记录为入口，所以码上的重量一定对得回那条设备事实。幂等靠 {@code weighing_event_id} 上的唯一索引： 重试命中已有码时原样返回，不再发第二枚。批次由现场选定，{@code bind}
     * 只校验商品与仓库一致—— 出库不按批次扣减，这里也不替库存账本做批次分配。
     */
    @Transactional(rollbackFor = Exception.class)
    public TraceScaleUnitVO issueForWeighing(TraceScaleUnitIssueForm form, String operator) {
        TraceUnitDao.WeighingEvent event = traceUnitDao.findWeighingEvent(form.getWeighingEventId());
        if (event == null || !form.getTaskItemId().equals(event.getTaskItemId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_WEIGHING_EVENT_NOT_FOUND);
        }
        TraceUnitVO.Unit existing = traceUnitDao.findByWeighingEventId(event.getId());
        if (existing != null) {
            return toScaleVO(existing, true);
        }
        if (event.getCompletionId() != null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_WEIGHING_EVENT_COMPLETED);
        }
        if (event.getNetWeight() == null || event.getNetWeight().signum() <= 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WEIGHT_INVALID);
        }
        TraceUnitVO.SortingItem item = traceUnitDao.lockSortingItem(form.getTaskItemId());
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        warehouseScopeGuard.require(item.getWarehouseId());
        TraceUnitVO.Lot lot = traceUnitDao.findLot(form.getLotId());
        if (lot == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
        if (!item.getSkuId().equals(lot.getSkuId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_SKU_MISMATCH);
        }
        Long lotWarehouseId = traceUnitDao.lotWarehouseId(form.getLotId());
        if (lotWarehouseId == null || !lotWarehouseId.equals(item.getWarehouseId())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WAREHOUSE_MISMATCH);
        }
        String lotUnit = traceUnitDao.lotUnit(form.getLotId());
        if (lotUnit == null || lotUnit.isBlank()) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }
        // 净重是销售单位口径，包装数量是批次记账单位口径，按订单行冻结的换算折一次
        BigDecimal quantity = saleToStockQuantity(item, event.getNetWeight(), lotUnit);
        BigDecimal balance = lockedBalance(form.getLotId());
        BigDecimal issued = traceUnitDao.sumIssuedByLot(form.getLotId());
        if (issued.add(quantity).compareTo(balance) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_EXCEED_BALANCE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        TraceUnitVO.Unit row = new TraceUnitVO.Unit();
        row.setUnitCode(nextCode());
        row.setPublicToken(nextPublicToken());
        row.setLotId(form.getLotId());
        row.setPackageType(form.getPackageType());
        row.setQuantity(quantity);
        row.setUnit(lotUnit);
        row.setNetWeight(event.getNetWeight());
        row.setWeightUnit(event.getBusinessUnit());
        row.setWeightSource(ScmTraceWeightSourceEnum.SCALE.name());
        row.setMeasuredAt(now);
        row.setWeighingEventId(event.getId());
        row.setCreatedBy(operator);
        traceUnitDao.createUnit(row);
        traceUnitDao.appendIssueEvent(row.getId(), form.getLotId(), now, operator, null);
        traceUnitDao.appendWeighEvent(row.getId(), form.getLotId(), now, operator, "电子秤称重");
        return toScaleVO(row, false);
    }

    /**
     * 明细完成时把该行已称重的码整批绑定。
     *
     * <p>
     * 不逐筐绑定：{@code bind} 校验的是「已绑定量 + 本码数量 ≤ 实拣量」，而实拣量要等集合完成才写入， 逐筐绑定会在第一筐就撞上 {@code TRACE_UNIT_BIND_EXCEED}。
     *
     * <p>
     * 装不下的码留在「未绑定」，不因此中断分拣完成：分拣完成是业务事实，追溯绑定是它的附属结果。 未绑定的码在包装码列表里仍然可见，可人工补绑或走更正——反过来把完成卡住，现场只会去删数据。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int bindWeighedUnits(Long sortingTaskItemId, String operator) {
        List<TraceUnitVO.Unit> units = traceUnitDao.listUnboundWeighedUnits(sortingTaskItemId);
        if (units.isEmpty()) {
            return 0;
        }
        TraceUnitVO.SortingItem item = traceUnitDao.lockSortingItem(sortingTaskItemId);
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        if (traceUnitDao.countConfirmedOutboundBySortingItem(sortingTaskItemId) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_ALREADY_OUTBOUND);
        }
        String stockUnit = item.getStockUnit() != null ? item.getStockUnit() : item.getSaleUnit();
        BigDecimal remaining = saleToStockQuantity(item, item.getSortedQuantity(), stockUnit)
                .subtract(traceUnitDao.sumBoundBySortingItem(sortingTaskItemId));
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal used = BigDecimal.ZERO;
        int bound = 0;
        for (TraceUnitVO.Unit unit : units) {
            if (used.add(unit.getQuantity()).compareTo(remaining) > 0) {
                break;
            }
            traceUnitDao.insertBinding(unit.getId(), unit.getLotId(), item.getTaskId(), sortingTaskItemId,
                    item.getSalesOrderId(), item.getSalesOrderItemId(), item.getCustomerId(), item.getCustomerName(),
                    item.getOrderNo(), now, operator);
            traceUnitDao.appendBindEvent(unit.getId(), unit.getLotId(), sortingTaskItemId, now, operator);
            used = used.add(unit.getQuantity());
            bound++;
        }
        return bound;
    }

    /** 秤端只需要码、链接与重量，不返回批次与客户等后台字段。 */
    private TraceScaleUnitVO toScaleVO(TraceUnitVO.Unit unit, boolean duplicated) {
        TraceScaleUnitVO result = new TraceScaleUnitVO();
        result.setUnitId(unit.getId());
        result.setUnitCode(unit.getUnitCode());
        result.setPublicToken(unit.getPublicToken());
        result.setTraceUrl(tracePublicLinkBuilder.build(unit.getPublicToken()));
        result.setNetWeight(unit.getNetWeight());
        result.setWeightUnit(unit.getWeightUnit());
        result.setQuantity(unit.getQuantity());
        result.setUnit(unit.getUnit());
        result.setDuplicated(duplicated);
        return result;
    }

    /** 秤端选批次：本仓该商品仍有余额的批次，按先进先出排序。 */
    @Transactional(readOnly = true)
    public List<TraceUnitVO.LotOption> candidateLots(Long sortingTaskItemId) {
        TraceUnitVO.SortingItem item = traceUnitDao.findSortingItem(sortingTaskItemId);
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        warehouseScopeGuard.require(item.getWarehouseId());
        return traceUnitDao.candidateLots(item.getWarehouseId(), item.getSkuId());
    }

    /** 秤端补打：按码取回二维码链接与重量，不重新发码。 */
    @Transactional(readOnly = true)
    public TraceScaleUnitVO label(String unitCode) {
        TraceUnitVO.Unit unit = traceUnitDao.findByCode(unitCode.trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
        return toScaleVO(unit, false);
    }

    /** 列表按仓库数据范围收窄：没有范围就是空页，不是「不加条件」。 */
    @Transactional(readOnly = true)
    public PageResult<TraceUnitVO.Unit> query(TraceUnitQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<TraceUnitVO.Unit> list = traceUnitDao.queryPage(page, form, scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 回放：单元 → 批次 → 收货来源 → 分拣绑定 → 出库去向。
     *
     * <p>
     * 出库去向是「该批次参与过的出库」，不等于「这一袋已经出库」——批次级分摊还证明不了单元级出库。 找不到码直接报不存在，不返回半截结果。
     */
    @Transactional(readOnly = true)
    public TraceUnitVO.Replay replay(String unitCode) {
        TraceUnitVO.Unit unit = traceUnitDao.findByCode(unitCode);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
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
     * 打印域只通过这里读包装码；数据范围与详情页同一套判定，找不到码按「不存在」回答。
     */
    @Transactional(readOnly = true)
    public TraceUnitVO.Replay printSource(Long unitId) {
        TraceUnitVO.Unit unit = traceUnitDao.findById(unitId);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
        TraceUnitVO.Replay replay = new TraceUnitVO.Replay();
        replay.setUnit(unit);
        replay.setLot(traceUnitDao.findLot(unit.getLotId()));
        replay.setBinding(traceUnitDao.findBinding(unit.getId()));
        return replay;
    }

    /** 预览沿用正式绑定守卫，但在事务代理内部转为不可处理原因，避免业务拒绝把只读预览标为 rollback-only。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String previewSortedQuantityRejection(Long sortingTaskItemId, BigDecimal newSortedQuantity) {
        try {
            requireBindingsFitSortedQuantity(sortingTaskItemId, newSortedQuantity);
            return null;
        } catch (ScmBusinessException exception) {
            return exception.getErrorCode().getMsg();
        }
    }

    /**
     * 分拣改量守卫（供分拣域调用的正式契约）：新实拣量不得低于该行仍有效绑定的包装总量。
     *
     * <p>
     * 必须在调用方事务内执行（MANDATORY）：本方法锁分拣行，与绑定共用同一把行锁， 否则「改量读到绑定 10」与「绑定读到实拣 10」可以交叉通过，留下绑定总量超过实拣量的状态。 要降量先走解绑更正，再改实拣量。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void requireBindingsFitSortedQuantity(Long sortingTaskItemId, BigDecimal newSortedQuantity) {
        TraceUnitVO.SortingItem item = traceUnitDao.lockSortingItem(sortingTaskItemId);
        if (item == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
        BigDecimal bound = traceUnitDao.sumBoundBySortingItem(sortingTaskItemId);
        if (bound.signum() <= 0) {
            return;
        }
        // 与 bind 同一口径：绑定量是库存记账单位，新实拣量是销售单位，按冻结换算统一后再比。
        // 绑定能存在就说明当时换算成立，这里用绑定侧的记账单位做基准。
        String stockUnit = item.getStockUnit() != null ? item.getStockUnit() : item.getSaleUnit();
        if (bound.compareTo(saleToStockQuantity(item, newSortedQuantity, stockUnit)) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_BIND_EXCEED);
        }
    }

    /**
     * 销售量折成库存记账量：实拣量是销售单位口径，包装码数量是批次余额的记账单位口径，两者不能直接比较。
     *
     * <p>
     * 优先用订单行确认时冻结的换算；没有冻结换算时，只有「销售单位就是记账单位」才能同口径比较，否则明确拒绝（41517）， 不猜比例。对侧的库存单位与冻结的库存单位不一致同样拒绝——那说明批次余额的记账单位在发码后变了口径。
     */
    private static BigDecimal saleToStockQuantity(TraceUnitVO.SortingItem item, BigDecimal saleQuantity,
            String stockUnit) {
        String frozenUnit = item.getStockUnit();
        BigDecimal factor = item.getStockUnitsPerSaleUnit();
        if (frozenUnit == null || factor == null) {
            if (item.getSaleUnit() == null || !item.getSaleUnit().equals(stockUnit)) {
                throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_UNIT_MISMATCH);
            }
            return InventorySaleUnitConverter.toStockQuantity(saleQuantity, null, null);
        }
        if (!frozenUnit.equals(stockUnit)) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_UNIT_MISMATCH);
        }
        return InventorySaleUnitConverter.toStockQuantity(saleQuantity, frozenUnit, factor);
    }

    /** 加锁读该批次余额：锁在行上，两个发码事务必须排队。 */
    private BigDecimal lockedBalance(Long lotId) {
        BigDecimal total = BigDecimal.ZERO;
        for (TraceUnitDao.LotBalanceRow row : traceUnitDao.lockLotBalances(lotId)) {
            total = total.add(row.getQuantity());
        }
        return total;
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

    /** 净重与每包数量共用同一套定点口径，但提示必须分开：补录时看不懂「每包数量」在说什么。 */
    private static BigDecimal parseWeight(String text) {
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (RuntimeException error) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WEIGHT_INVALID);
        }
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 4) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_WEIGHT_INVALID);
        }
        return value.setScale(4, java.math.RoundingMode.UNNECESSARY);
    }

    /** 公开令牌：随机而非序列，扫码入口不能靠猜相邻码拿到别人的溯源页。 */
    private static String nextPublicToken() {
        char[] token = new char[PUBLIC_TOKEN_LENGTH];
        for (int index = 0; index < PUBLIC_TOKEN_LENGTH; index++) {
            token[index] = CODE_ALPHABET[TOKEN_RANDOM.nextInt(CODE_ALPHABET.length)];
        }
        return new String(token);
    }
}
