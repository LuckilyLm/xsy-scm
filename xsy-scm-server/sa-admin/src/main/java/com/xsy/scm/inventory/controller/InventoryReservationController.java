package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryReservationQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryReservationVO;
import com.xsy.scm.inventory.service.InventoryReservationQueryService;
import com.xsy.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存预留（查询 + 释放）。
 *
 * <p>
 * <b>为什么没有通用「新建预留」端点</b>：预留必须挂在销售订单上，不能脱离业务单据手工创建。
 * 当前由销售订单的显式“预留库存”命令调用 {@code InventoryReservationService.reserve}，以支持“先接单、后采购”。
 */
@RestController
@RequestMapping("/scm/inventory/reservation")
@Tag(name = "SCM 库存预留")
@RequiredArgsConstructor
public class InventoryReservationController {

    private final InventoryReservationService inventoryReservationService;

    private final InventoryReservationQueryService inventoryReservationQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.RESERVATION_QUERY)
    public ResponseDTO<PageResult<InventoryReservationVO>> query(
            @Valid @RequestBody InventoryReservationQueryForm form) {
        return ResponseDTO.ok(inventoryReservationQueryService.queryPage(form));
    }

    /**
     * 释放预留：占用归还可用量。
     *
     * <p>
     * 只有生效中的预留可释放；重复释放会失败（41009），不会把可用量虚增。
     */
    @PostMapping("/release/{id}")
    @SaCheckPermission(InventoryPermission.RESERVATION_RELEASE)
    @OperateLog
    public ResponseDTO<String> release(@PathVariable("id") Long reservationId) {
        inventoryReservationService.release(reservationId);
        return ResponseDTO.ok();
    }
}
