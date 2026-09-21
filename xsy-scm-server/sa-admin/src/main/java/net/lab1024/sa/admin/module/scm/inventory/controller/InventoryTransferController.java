package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryInTransitVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 库存调拨单（跨仓，两步式：发出 → 在途 → 收货）。
 *
 * <p>共 7 个端点：分页 / 详情 / 新建 / 改草稿 / 发出 / 收货 / 取消 / 删除（8 个，其中取消与删除分开）。
 *
 * <p><b>权限划分</b>：查询 {@code scm:inventory:transfer:query}、
 * 新建 {@code :add}、改草稿 {@code :update}、发出 {@code :ship}、收货 {@code :receive}、
 * 删除 {@code :delete}。
 *
 * <p><b>「发出」与「收货」是两个独立权限</b>：跨仓调拨的常见分工是源仓发货、目标仓点收，
 * 由同一个人两头都确认会让在途数量失去复核 —— 而在途数量正是最容易出错的地方。
 */
@RestController
@RequestMapping("/scm/inventory/transfer")
@Tag(name = "SCM 库存调拨单")
@RequiredArgsConstructor
public class InventoryTransferController {

    private final InventoryTransferService service;

    private final InventoryTransferQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:transfer:query")
    public ResponseDTO<PageResult<InventoryTransferVO>> query(@Valid @RequestBody InventoryTransferQueryForm form) {
        return ResponseDTO.ok(queryService.queryPage(form));
    }

    /**
     * 在途库存报表（只读聚合，把 SHIPPED 调拨单展开成明细行）。
     */
    @GetMapping("/in-transit")
    @SaCheckPermission("scm:inventory:transfer:query")
    public ResponseDTO<List<InventoryInTransitVO>> inTransit() {
        return ResponseDTO.ok(queryService.queryInTransit());
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:inventory:transfer:query")
    public ResponseDTO<InventoryTransferVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    /**
     * 新建草稿调拨单，返回新单 id。
     */
    @PostMapping("/create")
    @SaCheckPermission("scm:inventory:transfer:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryTransferAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    /**
     * 改草稿（仅 DRAFT）。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("scm:inventory:transfer:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id,
                                      @Valid @RequestBody InventoryTransferAddForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 发出：从源仓扣减并写 {@code TRANSFER_OUT} 流水，单据进入**在途**。
     *
     * <p>这是两步式的第一步。发出后源仓库存立即减少、目标仓尚未增加 ——
     * 期间这批货不在任何余额行里（没有虚拟在途仓），全仓总库存会暂时减少。
     */
    @PostMapping("/ship/{id}")
    @SaCheckPermission("scm:inventory:transfer:ship")
    @OperateLog
    public ResponseDTO<String> ship(@PathVariable Long id) {
        service.ship(id);
        return ResponseDTO.ok();
    }

    /**
     * 收货：向目标仓累加并写 {@code TRANSFER_IN} 流水，单据完成。
     *
     * <p>目标仓的记账单位必须与调拨单位一致（41044）—— 库存不做自动换算。
     */
    @PostMapping("/receive/{id}")
    @SaCheckPermission("scm:inventory:transfer:receive")
    @OperateLog
    public ResponseDTO<String> receive(@PathVariable Long id) {
        service.receive(id);
        return ResponseDTO.ok();
    }

    /**
     * 取消草稿（不产生任何库存影响）。在途不可取消 —— 货已出库，只能反向调拨冲回。
     */
    @PostMapping("/cancel/{id}")
    @SaCheckPermission("scm:inventory:transfer:update")
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseDTO.ok();
    }

    /**
     * 删除草稿（逻辑删）。已发出 / 已收货的单不可删除。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("scm:inventory:transfer:delete")
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseDTO.ok();
    }
}
