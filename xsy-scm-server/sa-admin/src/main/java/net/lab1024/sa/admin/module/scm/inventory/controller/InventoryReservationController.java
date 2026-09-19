package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryReservationQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryReservationVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
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
 * <p><b>为什么没有「新建预留」端点</b>：预留是**业务动作的副产物**，不是人手工录的单据。
 * 本波次由销售订单确认触发（走 {@code InventoryReservationService.reserve}），
 * 对外只暴露查询与释放。开放手工预留会让「谁占了这批货」失去业务依据。
 */
@RestController
@RequestMapping("/scm/inventory/reservation")
@Tag(name = "SCM 库存预留")
@RequiredArgsConstructor
public class InventoryReservationController {

    private final InventoryReservationService service;

    private final InventoryReservationQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:reservation:query")
    public ResponseDTO<PageResult<InventoryReservationVO>> query(
            @Valid @RequestBody InventoryReservationQueryForm form) {
        return ResponseDTO.ok(queryService.queryPage(form));
    }

    /**
     * 释放预留：占用归还可用量。
     *
     * <p>只有生效中的预留可释放；重复释放会失败（41009），不会把可用量虚增。
     */
    @PostMapping("/release/{id}")
    @SaCheckPermission("scm:inventory:reservation:release")
    @OperateLog
    public ResponseDTO<String> release(@PathVariable Long id) {
        service.release(id);
        return ResponseDTO.ok();
    }
}
