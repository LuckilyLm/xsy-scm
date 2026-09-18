package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryMovementQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryMovementVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryMovementQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存流水（W6 Target Design §10.1）——**全只读**。
 *
 * <p>流水是 append-only 账本（Q7）：没有新增、没有编辑、没有删除端点。
 * 未来冲销以「新增反向 movement」实现，同样不会出现「改历史流水」的 API。
 *
 * <p>权限码 {@code scm:inventory:movement:query}（菜单 821）。
 */
@RestController
@RequestMapping("/scm/inventory/movement")
@Tag(name = "SCM 库存流水")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:movement:query")
    public ResponseDTO<PageResult<InventoryMovementVO>> query(
            @Valid @RequestBody InventoryMovementQueryForm form) {
        return ResponseDTO.ok(queryService.query(form));
    }
}
