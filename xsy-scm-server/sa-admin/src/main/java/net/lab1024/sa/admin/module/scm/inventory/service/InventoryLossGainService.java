package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryLossGainDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryLossGainItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryLossGainFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryLossGainEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryLossGainItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainItemVO;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import net.lab1024.sa.base.module.support.message.constant.MessageTypeEnum;
import net.lab1024.sa.base.module.support.message.domain.MessageSendForm;
import net.lab1024.sa.base.module.support.message.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_EMPTY_ITEMS;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_DUPLICATE_SKU;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_REJECT_OPINION_REQUIRED;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_STATUS_INVALID;

/**
 * 报损报溢单命令侧：创建 / 改待审核 / 审批 / 驳回 / 删除。
 *
 * <p><b>状态机</b>：{@code PENDING → COMPLETED | REJECTED}，两个终态都不可回退。
 * 只有 {@code PENDING} 可改 / 可删 / 可审 —— 与参考项目不同，参考项目对 update / delete
 * 没有状态守卫，那会让「已完成（已写流水）」的单据被改内容或被删掉，账与单从此对不上。
 *
 * <p><b>锁序（与收货 / 出库 / 盘点同一顺序）</b>：
 * <ol>
 *   <li>先锁单据头（{@code lockById}）；</li>
 *   <li>再按 {@code (warehouseId, skuId)} **升序**逐行锁余额并写流水。</li>
 * </ol>
 * 顺序固定是避免四条链路以相反顺序拿余额锁而死锁。
 *
 * <p><b>全部明细在同一事务内</b>：任一行失败（负库存 / 低于预留 / 无余额行）整单回滚 ——
 * 不允许「报一半」。已写下的流水也随事务回滚。
 *
 * <p><b>审批用乐观锁</b>：审批人必须批准自己读到的内容。若在「打开单据 → 点审批」之间
 * 录单人改了明细，版本已经前进，审批以 40921 失败并要求刷新。
 * 版本判断在**写流水之前**先做一次（早失败，不做无用功），
 * 同时保留在 SQL 的 {@code WHERE} 里作为并发下的第二道防线。
 */
@Service
@RequiredArgsConstructor
public class InventoryLossGainService {

    private final InventoryLossGainDao lossGainDao;

    private final InventoryLossGainItemDao itemDao;

    private final InventoryLossGainNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    private final MessageService messageService;

    /**
     * 新建报损报溢单（**创建即待审核**）。
     *
     * <p>不校验「能不能真的调整」：待审核阶段不影响库存，负库存 / 低于预留都要等到审批时
     * 才知道（期间可能有出库）。提前卡住会让录单不可用。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryLossGainAddForm form) {
        requireItems(form);
        requireKnownType(form.getAdjustType());
        String operator = ScmOperator.current();
        // 仓库不存在时给出准确错误，而不是让审批阶段退化成「没有库存记录」。
        warehouseService.require(form.getWarehouseId());

        InventoryLossGainEntity entity = new InventoryLossGainEntity();
        entity.setLossGainNo(numberGenerator.next());
        entity.setWarehouseId(form.getWarehouseId());
        entity.setAdjustType(form.getAdjustType());
        entity.setStatus(ScmInventoryLossGainStatusEnum.PENDING.name());
        entity.setReason(form.getReason());
        entity.setRemark(form.getRemark());
        // 待审核态不得有审核信息（DB CHECK 也要求两者同时为空）
        entity.setAuditedAt(null);
        entity.setAuditor(null);
        entity.setAuditOpinion(null);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        lossGainDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /**
     * 改待审核单据：只允许 PENDING；明细整表替换（逻辑删旧 + 插新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryLossGainAddForm form) {
        requireItems(form);
        requireKnownType(form.getAdjustType());
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());

        InventoryLossGainEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryLossGainStatusEnum.PENDING);

        if (lossGainDao.updatePending(id, form.getAdjustType(), form.getWarehouseId(),
                form.getReason(), form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        itemDao.deleteByLossGainId(id, operator);
        insertItems(id, form, operator);
    }

    /**
     * 审批通过：按单据类型写 {@code LOSS_REPORT} / {@code GAIN_REPORT} 流水并调整余额。
     *
     * <p>先锁单据再锁余额；余额按 {@code (warehouseId, skuId)} 升序处理。
     * 明细行的 {@code unitSnapshot} 在此刻按余额记账单位回写 —— 待审核态它为空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, InventoryLossGainAuditForm form) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryLossGainEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryLossGainStatusEnum.PENDING);
        requireVersion(locked, form);
        // 类型未知就不该继续 —— 方向无法确定，不能猜。
        ScmInventoryLossGainTypeEnum type =
                ScmInventoryLossGainTypeEnum.of(locked.getAdjustType());
        if (type == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }

        List<InventoryLossGainItemVO> items = itemDao.listByLossGainId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_EMPTY_ITEMS);
        }

        // 锁序：余额锁按 (warehouseId, skuId) 升序 —— 同一单内多行也必须固定顺序。
        items.stream()
                .sorted(Comparator.comparing(InventoryLossGainItemVO::getSkuId))
                .forEach(item -> {
                    InventoryLossGainFact fact = new InventoryLossGainFact(
                            locked.getWarehouseId(),
                            item.getSkuId(),
                            locked.getId(),
                            item.getId(),
                            locked.getAdjustType(),
                            item.getQuantity(),
                            now,
                            operator);
                    // 单位以余额记账单位为准（Q13），由命令服务返回，这里回写到明细行
                    String unit = inventoryCommandService.postLossGainAdjust(fact);
                    itemDao.updateUnitSnapshot(item.getId(), unit, operator);
                });

        if (lossGainDao.markCompleted(id, now, operator, form.getAuditOpinion(), form.getVersion()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 驳回：只允许 PENDING，**不产生任何库存影响**。
     *
     * <p>审核意见必填（41037）：驳回是唯一会把「为什么不行」传达给录单人的渠道，
     * 允许空意见的驳回会让录单人只能反复试。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, InventoryLossGainAuditForm form) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        if (form.getAuditOpinion() == null || form.getAuditOpinion().isBlank()) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_REJECT_OPINION_REQUIRED);
        }

        InventoryLossGainEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryLossGainStatusEnum.PENDING);
        requireVersion(locked, form);

        if (lossGainDao.markRejected(id, now, operator, form.getAuditOpinion(), form.getVersion()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        // 驳回是唯一把「为什么不行」传达给录单人的渠道；与状态变更同事务写站内信，
        // 回滚不留通知。并发 / 重复驳回只有一次 markRejected==1 能走到这里，故通知至多一条。
        notifyMakerRejected(locked, form.getAuditOpinion());
    }

    /**
     * 删除待审核单据（逻辑删）。已审核的单不可删 —— 它们必须留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String operator = ScmOperator.current();
        InventoryLossGainEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryLossGainStatusEnum.PENDING);
        itemDao.deleteByLossGainId(id, operator);
        if (lossGainDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void insertItems(Long lossGainId, InventoryLossGainAddForm form, String operator) {
        for (InventoryLossGainAddForm.Item item : form.getItems()) {
            InventoryLossGainItemEntity row = new InventoryLossGainItemEntity();
            row.setLossGainId(lossGainId);
            row.setSkuId(item.getSkuId());
            row.setQuantity(item.getQuantity());
            row.setRemark(item.getRemark());
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            itemDao.insert(row);
        }
    }

    /**
     * 明细校验：至少一行，且同一 SKU 不得重复。
     *
     * <p>重复 SKU 会让同一份数量被调整两次，而结果看起来完全正常（余额确实变了），
     * 只是变错了。因此必须在写库前挡掉，而不是静默去重。
     */
    private static void requireItems(InventoryLossGainAddForm form) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_EMPTY_ITEMS);
        }
        Set<Long> seen = new HashSet<>();
        for (InventoryLossGainAddForm.Item item : form.getItems()) {
            if (item == null || item.getSkuId() == null || !seen.add(item.getSkuId())) {
                throw new ScmBusinessException(INVENTORY_LOSS_GAIN_DUPLICATE_SKU);
            }
        }
    }

    /**
     * 类型白名单（与 DB CHECK / 前端枚举同源；@Pattern 已挡一层，这里是服务层兜底）。
     */
    private static void requireKnownType(String adjustType) {
        if (!ScmInventoryLossGainTypeEnum.isSupported(adjustType)) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }
    }

    /**
     * 乐观锁早失败：版本不符时**在写流水之前**就报错，不做无用功。
     *
     * <p>SQL 的 {@code WHERE version = ?} 仍是必需的 —— 它才是并发下真正生效的那道；
     * 这里的判断只是让错误来得更早、更明确。
     */
    private static void requireVersion(InventoryLossGainEntity entity, InventoryLossGainAuditForm form) {
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private InventoryLossGainEntity lockAndRequire(Long id) {
        InventoryLossGainEntity locked = lossGainDao.lockById(id);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryLossGainEntity entity,
                                      ScmInventoryLossGainStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_STATUS_INVALID);
        }
    }

    /**
     * 驳回后向制单人发一条站内信。制单人取自单据 {@code created_by}
     * （{@link ScmOperator} 写入的 {@code userType:userId} 串），解析不出合法接收人时跳过——
     * 不能因为一条历史脏数据把有效的驳回整体回滚。
     */
    private void notifyMakerRejected(InventoryLossGainEntity order, String opinion) {
        String creator = order.getCreatedBy();
        if (creator == null) {
            return;
        }
        String[] parts = creator.split(":");
        if (parts.length != 2) {
            return;
        }
        try {
            MessageSendForm form = new MessageSendForm();
            // 业务类型走 messageType：前端按它决定「查看业务单据」跳转，不解析中文标题。
            form.setMessageType(MessageTypeEnum.SCM_INVENTORY_LOSS_GAIN.getValue());
            form.setReceiverUserType(Integer.parseInt(parts[0]));
            form.setReceiverUserId(Long.parseLong(parts[1]));
            form.setTitle("报损报溢单被驳回");
            form.setContent("您提交的报损报溢单 " + order.getLossGainNo() + " 已被驳回：" + opinion);
            form.setDataId(order.getId());
            messageService.sendMessage(form);
        } catch (NumberFormatException e) {
            // created_by 非预期的 "userType:userId" 格式：跳过通知，不阻断驳回
        }
    }
}
