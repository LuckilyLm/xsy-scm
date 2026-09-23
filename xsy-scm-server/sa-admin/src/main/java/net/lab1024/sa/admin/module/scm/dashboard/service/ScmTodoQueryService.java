package net.lab1024.sa.admin.module.scm.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.dashboard.constant.ScmTodoCardEnum;
import net.lab1024.sa.admin.module.scm.dashboard.domain.vo.ScmTodoVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.UserPermission;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务待办聚合（只读）。实时聚合现有业务表，不落任何快照表、不新增消息表。
 *
 * <p>可见性 = 待办入口权限（由控制器 {@code scm:todo:query} 保证）∩ 本卡片领域权限，
 * 由 {@link ScmTodoCardEnum#visibleTo} 判定；无权卡片整卡省略、既不给数字也不填 0，
 * 有权但零任务才返回 0。计数一律复用对应领域查询服务的分页 {@code total}，
 * 不重写筛选口径、异常判定或数据范围。
 */
@Service
@RequiredArgsConstructor
public class ScmTodoQueryService {

    private final LoginManager loginManager;
    private final InventoryWarningQueryService warningQueryService;
    private final PurchaseQueryService purchaseQueryService;
    private final InventoryLossGainQueryService lossGainQueryService;
    private final DeliveryRouteQueryService deliveryRouteQueryService;

    /**
     * 当前登录员工的待办卡片列表。
     */
    public List<ScmTodoVO> currentEmployeeTodos() {
        RequestUser user = SmartRequestUtil.getRequestUser();
        if (user == null || user.getUserId() == null) {
            return List.of();
        }
        UserPermission permission = loginManager.getUserPermission(user.getUserId());
        List<String> held = permission == null ? List.of() : permission.getPermissionList();
        return todosFor(held);
    }

    /**
     * 按给定权限集合计算卡片（与登录态解耦，便于负向夹具直接验证省略 / 零值语义）。
     */
    public List<ScmTodoVO> todosFor(List<String> heldPermissions) {
        List<String> held = heldPermissions == null ? List.of() : heldPermissions;
        List<ScmTodoVO> result = new ArrayList<>();
        for (ScmTodoCardEnum card : ScmTodoCardEnum.values()) {
            if (!card.visibleTo(held)) {
                continue;
            }
            ScmTodoVO vo = new ScmTodoVO();
            vo.setKey(card.getKey());
            vo.setLabel(card.getLabel());
            vo.setRoute(card.getRoute());
            vo.setCount(countOf(card));
            result.add(vo);
        }
        return result;
    }

    private long countOf(ScmTodoCardEnum card) {
        return switch (card) {
            // status 为空即预警列表默认口径：只含异常（LOW / HIGH），与列表页一致
            case INVENTORY_WARNING -> total(warningQueryService.queryWarningPage(newWarningForm()));
            case RECEIPT_PUTAWAY -> total(purchaseQueryService.receiptQuery(putawayForm()));
            // PENDING 即待审批列表筛选，approve / reject 权限由卡片可见性把关
            case LOSS_GAIN_AUDIT -> total(lossGainQueryService.queryPage(pendingLossGainForm()));
            case DELIVERY_ROUTE_DRAFT -> total(deliveryRouteQueryService.query(draftRouteForm()));
        };
    }

    private static long total(PageResult<?> page) {
        return page == null || page.getTotal() == null ? 0L : page.getTotal();
    }

    private static InventoryWarningQueryForm newWarningForm() {
        InventoryWarningQueryForm form = new InventoryWarningQueryForm();
        form.setPageNum(1L);
        form.setPageSize(1L);
        return form;
    }

    private static PurchaseReceiptQueryForm putawayForm() {
        PurchaseReceiptQueryForm form = new PurchaseReceiptQueryForm();
        form.setPageNum(1L);
        form.setPageSize(1L);
        form.setStatus("CONFIRMED");
        form.setReceiptMode("WAREHOUSE_CONFIRM");
        form.setPutawayStatus("PENDING");
        return form;
    }

    private static InventoryLossGainQueryForm pendingLossGainForm() {
        InventoryLossGainQueryForm form = new InventoryLossGainQueryForm();
        form.setPageNum(1L);
        form.setPageSize(1L);
        form.setStatus("PENDING");
        return form;
    }

    private static DeliveryQueryForm draftRouteForm() {
        DeliveryQueryForm form = new DeliveryQueryForm();
        form.setPageNum(1L);
        form.setPageSize(1L);
        form.setStatus("DRAFT");
        return form;
    }
}
