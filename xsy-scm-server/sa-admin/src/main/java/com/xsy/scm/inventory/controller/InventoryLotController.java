package com.xsy.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.inventory.domain.form.InventoryLotBalanceQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryLotMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryLotBalanceVO;
import com.xsy.scm.inventory.domain.vo.InventoryLotMovementVO;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.inventory.service.InventoryLotQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 批次库存——<b>全只读</b>。
 *
 * <p>
 * 批次余额是子账，不是可以被直接赋值的状态：写入只发生在采购收货确认与销售出库命令里，与总账同事务。 两个端点共用权限码 {@code scm:inventory:lot:query}（菜单 2101 与之逐字对应，由权限 IT
 * 校验）。
 */
@RestController
@RequestMapping("/scm/inventory/lot")
@Tag(name = "SCM 批次库存")
@RequiredArgsConstructor
public class InventoryLotController {

    private final InventoryLotQueryService inventoryLotQueryService;

    @PostMapping("/balance/query")
    @SaCheckPermission(InventoryPermission.LOT_QUERY)
    public ResponseDTO<PageResult<InventoryLotBalanceVO>> queryBalance(
        @RequestBody @Valid InventoryLotBalanceQueryForm form) {
        return ResponseDTO.ok(inventoryLotQueryService.queryBalance(form));
    }

    @PostMapping("/movement/query")
    @SaCheckPermission(InventoryPermission.LOT_QUERY)
    public ResponseDTO<PageResult<InventoryLotMovementVO>> queryMovement(
        @RequestBody @Valid InventoryLotMovementQueryForm form) {
        return ResponseDTO.ok(inventoryLotQueryService.queryMovement(form));
    }
}
