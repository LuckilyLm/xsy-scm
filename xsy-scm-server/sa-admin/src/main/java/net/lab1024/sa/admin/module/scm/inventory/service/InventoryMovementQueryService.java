package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryMovementQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryMovementVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存流水只读查询（W6 Target Design §10.1）。
 *
 * <p><b>只读</b>：流水表是 append-only 账本（Q7），本类只查不改；
 * 全库范围内也没有任何端点能修改历史流水。
 *
 * <p>时间范围过滤的是 {@code occurred_at}（业务发生时刻 = 收货确认时刻），
 * 不是 {@code created_at}：backfill 回放的历史收货，其写入时刻是迁移执行时刻，
 * 但业务发生时刻是历史确认时刻 —— 用 {@code created_at} 过滤会让这部分数据查不到。
 *
 * <p>排序固定为 {@code occurred_at DESC, id DESC}（最新发生的在最前），
 * 与余额页同样拒绝客户端排序（见 {@code InventoryBalanceQueryService} 的类注释）。
 */
@Service
@RequiredArgsConstructor
public class InventoryMovementQueryService {

    private final InventoryMovementDao movementDao;

    @Transactional(readOnly = true)
    public PageResult<InventoryMovementVO> query(InventoryMovementQueryForm form) {
        InventoryBalanceQueryService.rejectClientSort(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, movementDao.queryPage(page, form));
    }
}
