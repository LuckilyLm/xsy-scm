package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryBalanceQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryBalanceVO;
import com.xsy.scm.inventory.service.InventoryBalanceQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存余额——<b>全只读</b>。
 *
 * <p>
 * 没有手工写 API：库存余额不是可以被直接赋值的状态，它只能是流水的净和。的唯一写入路径是「收货确认 → {@code PURCHASE_IN}」（同事务）。
 *
 * <p>
 * 两个端点共用同一个权限码 {@code scm:inventory:balance:query} （菜单 811 与之逐字对应，由权限 IT 校验代码与菜单不脱节）。只读端点不加 {@code @OperateLog}（与
 * 查询端点一致）。
 */
@RestController
@RequestMapping("/scm/inventory/balance")
@Tag(name = "SCM 库存余额")
@RequiredArgsConstructor
public class InventoryBalanceController {

    private final InventoryBalanceQueryService inventoryBalanceQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.BALANCE_QUERY)
    public ResponseDTO<PageResult<InventoryBalanceVO>> query(@Valid @RequestBody InventoryBalanceQueryForm form) {
        return ResponseDTO.ok(inventoryBalanceQueryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(InventoryPermission.BALANCE_QUERY)
    public ResponseDTO<InventoryBalanceVO> detail(@PathVariable("id") Long balanceId) {
        return ResponseDTO.ok(inventoryBalanceQueryService.detail(balanceId));
    }
}
