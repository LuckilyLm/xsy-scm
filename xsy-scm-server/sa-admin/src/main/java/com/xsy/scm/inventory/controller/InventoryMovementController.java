package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryMovementVO;
import com.xsy.scm.inventory.service.InventoryMovementQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存流水——**全只读**。
 *
 * <p>流水是 append-only 账本：没有新增、没有编辑、没有删除端点。
 * 未来冲销以「新增反向 movement」实现，同样不会出现「改历史流水」的 API。
 *
 * <p>权限码 {@code scm:inventory:movement:query}（菜单 821）。
 */
@RestController
@RequestMapping("/scm/inventory/movement")
@Tag(name = "SCM 库存流水")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementQueryService inventoryMovementQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.MOVEMENT_QUERY)
    public ResponseDTO<PageResult<InventoryMovementVO>> query(
            @Valid @RequestBody InventoryMovementQueryForm form) {
        return ResponseDTO.ok(inventoryMovementQueryService.query(form));
    }
}
