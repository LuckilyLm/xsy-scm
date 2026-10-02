package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningScanVO;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.inventory.service.InventoryWarningScanService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存预警。
 *
 * <p>
 * 两个端点：预警列表（只读）与主动检查并投递通知（写站内信）。
 *
 * <p>
 * 预警列表刻意**没有**「标记已读 / 已忽略」这类写操作 —— 预警不是一种状态，它只是
 * {@code (阈值, 可用量)} 的当前计算结果。引入「已读」会让预警与真实库存脱钩：货补上了，
 * 那条「已读」记录还在；货又少了，它却已经被忽略过。用户想看什么，就按状态筛什么。
 *
 * <p>
 * 权限 {@code scm:inventory:warning:query} 与阈值配置分开：预警列表是**只读**的日常查看
 * （仓管每天看），阈值配置是**改规则**（改错了会让预警失效或刷屏），两者不是同一量级的操作。
 * 主动检查再单独用 {@code scm:inventory:warning:scan} 授权 —— 它会**给别人发站内信**，
 * 拿到只读的查看权不应该顺带获得这个能力。
 */
@RestController
@RequestMapping("/scm/inventory/warning")
@Tag(name = "SCM 库存预警")
@RequiredArgsConstructor
public class InventoryWarningController {

    private final InventoryWarningQueryService inventoryWarningQueryService;

    private final InventoryWarningScanService inventoryWarningScanService;

    private final ScmDataScopeService dataScopeService;

    /**
     * 预警列表。
     *
     * <p>
     * {@code status} 为空 → 只返回异常项（低于下限 / 高于上限）；这是预警列表的默认语义。 传 {@code NORMAL} 才看正常项。
     */
    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.WARNING_QUERY)
    public ResponseDTO<PageResult<InventoryWarningVO>> query(@Valid @RequestBody InventoryWarningQueryForm form) {
        return ResponseDTO.ok(inventoryWarningQueryService.queryWarningPage(form));
    }

    /**
     * 立即检查阈值跃迁并投递通知（不等下一轮定时扫描）。
     *
     * <p>
     * 只扫描调用者**有授权的仓库**：定时任务按全部仓库跑，手动触发没有理由越过自己的范围。
     * 重复点击不会重复发信 —— 同一次跃迁的 event_key 是稳定的，第二次起会被去重表挡掉。
     */
    @PostMapping("/scan")
    @SaCheckPermission(InventoryPermission.WARNING_SCAN)
    public ResponseDTO<InventoryWarningScanVO> scan() {
        return ResponseDTO.ok(inventoryWarningScanService.scan(dataScopeService.resolve().getWarehouseScope()));
    }
}
