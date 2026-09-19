package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存盘点单。
 *
 * <p>共 7 个端点：分页 / 详情 / 新建 / 改草稿 / 确认盘点 / 取消 / 删除。
 *
 * <p><b>权限划分</b>：查询 {@code scm:inventory:stocktake:query}、
 * 新建 {@code :add}、改草稿 {@code :update}、确认盘点 {@code :confirm}、删除 {@code :delete}。
 * 「确认盘点」是**独立的权限**而不是复用 {@code :update} —— 确认会真实调整库存并写不可逆流水，
 * 与「改个草稿」不是同一量级的操作，允许仓管录实盘数但由主管确认是完全合理的分工。
 * 与出库单保持同一取向。
 */
@RestController
@RequestMapping("/scm/inventory/stocktake")
@Tag(name = "SCM 库存盘点单")
@RequiredArgsConstructor
public class InventoryStocktakeController {

    private final InventoryStocktakeService service;

    private final InventoryStocktakeQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:stocktake:query")
    public ResponseDTO<PageResult<InventoryStocktakeVO>> query(@Valid @RequestBody InventoryStocktakeQueryForm form) {
        return ResponseDTO.ok(queryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:inventory:stocktake:query")
    public ResponseDTO<InventoryStocktakeVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    /** 新建草稿盘点单，返回新单 id。 */
    @PostMapping("/create")
    @SaCheckPermission("scm:inventory:stocktake:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryStocktakeAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    /** 改草稿（仅 DRAFT）；会重新快照账面量。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("scm:inventory:stocktake:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id,
                                      @Valid @RequestBody InventoryStocktakeAddForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 确认盘点：差异转盘盈 / 盘亏流水并调整余额。
     *
     * <p>这是本模块唯一会改变库存的端点，失败整单回滚，不存在「盘一半」。
     */
    @PostMapping("/confirm/{id}")
    @SaCheckPermission("scm:inventory:stocktake:confirm")
    @OperateLog
    public ResponseDTO<String> confirm(@PathVariable Long id) {
        service.confirm(id);
        return ResponseDTO.ok();
    }

    /** 取消草稿（不产生任何库存影响）。 */
    @PostMapping("/cancel/{id}")
    @SaCheckPermission("scm:inventory:stocktake:update")
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseDTO.ok();
    }

    /** 删除草稿（逻辑删）。已确认的单不可删。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("scm:inventory:stocktake:delete")
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseDTO.ok();
    }
}
