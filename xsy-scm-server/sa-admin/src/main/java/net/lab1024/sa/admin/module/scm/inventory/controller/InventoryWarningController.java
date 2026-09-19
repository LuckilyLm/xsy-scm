package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存预警（**只读**）。
 *
 * <p>只有一个端点：预警列表。这里刻意**没有任何写操作** —— 预警不是一种可以「标记已读」
 * 的状态，它只是 {@code (阈值, 可用量)} 的当前计算结果。引入「已读 / 已忽略」会让预警
 * 与真实库存脱钩：货补上了，那条「已读」的记录还在；货又少了，它却已经被忽略过。
 * 用户想看什么，就按状态筛什么。
 *
 * <p>权限 {@code scm:inventory:warning:query} 与阈值配置分开：预警列表是**只读**的日常查看
 * （仓管每天看），阈值配置是**改规则**（改错了会让预警失效或刷屏），两者不是同一量级的操作。
 */
@RestController
@RequestMapping("/scm/inventory/warning")
@Tag(name = "SCM 库存预警")
@RequiredArgsConstructor
public class InventoryWarningController {

    private final InventoryWarningQueryService queryService;

    /**
     * 预警列表。
     *
     * <p>{@code status} 为空 → 只返回异常项（低于下限 / 高于上限）；这是预警列表的默认语义。
     * 传 {@code NORMAL} 才看正常项。
     */
    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:warning:query")
    public ResponseDTO<PageResult<InventoryWarningVO>> query(@Valid @RequestBody InventoryWarningQueryForm form) {
        return ResponseDTO.ok(queryService.queryWarningPage(form));
    }
}
