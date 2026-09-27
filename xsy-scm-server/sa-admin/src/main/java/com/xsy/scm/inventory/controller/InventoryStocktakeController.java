package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryStocktakeAddForm;
import com.xsy.scm.inventory.domain.form.InventoryStocktakeQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryStocktakeImportResultVO;
import com.xsy.scm.inventory.domain.vo.InventoryStocktakeVO;
import com.xsy.scm.inventory.service.InventoryStocktakeImportService;
import com.xsy.scm.inventory.service.InventoryStocktakeQueryService;
import com.xsy.scm.inventory.service.InventoryStocktakeService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * SCM 库存盘点单。
 *
 * <p>
 * 共 7 个端点：分页 / 详情 / 新建 / 改草稿 / 确认盘点 / 取消 / 删除。
 *
 * <p>
 * <b>权限划分</b>：查询 {@code scm:inventory:stocktake:query}、 新建 {@code :add}、改草稿 {@code :update}、确认盘点 {@code :confirm}、删除
 * {@code :delete}。 「确认盘点」是**独立的权限**而不是复用 {@code :update} —— 确认会真实调整库存并写不可逆流水， 与「改个草稿」不是同一量级的操作，允许仓管录实盘数但由主管确认是完全合理的分工。
 * 与出库单保持同一取向。
 */
@RestController
@RequestMapping("/scm/inventory/stocktake")
@Tag(name = "SCM 库存盘点单")
@RequiredArgsConstructor
public class InventoryStocktakeController {

    private final InventoryStocktakeService inventoryStocktakeService;

    private final InventoryStocktakeQueryService inventoryStocktakeQueryService;

    private final InventoryStocktakeImportService inventoryStocktakeImportService;

    private final SecurityFileService securityFileService;

    private static final long MAX_IMPORT_SIZE = 10L * 1024 * 1024;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_QUERY)
    public ResponseDTO<
            PageResult<
                    InventoryStocktakeVO>> query(@Valid @RequestBody InventoryStocktakeQueryForm form) {
        return ResponseDTO.ok(inventoryStocktakeQueryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_QUERY)
    public ResponseDTO<
            InventoryStocktakeVO> detail(@PathVariable("id") Long stocktakeId) {
        return ResponseDTO.ok(inventoryStocktakeQueryService.detail(stocktakeId));
    }

    /**
     * 新建草稿盘点单，返回新单 id。
     */
    @PostMapping("/create")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_ADD)
    @OperateLog
    public ResponseDTO<
            Long> create(@Valid @RequestBody InventoryStocktakeAddForm form) {
        return ResponseDTO.ok(inventoryStocktakeService.create(form));
    }

    /**
     * 导出某仓库的盘点 Excel 模板（含签名快照凭证）。
     *
     * <p>
     * 需要导入权限；模板里的账面量 / 单位来自余额，读取受 {@code:import} 约束（计划）。
     */
    @GetMapping("/import/template")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_IMPORT)
    public void importTemplate(@RequestParam Long warehouseId, HttpServletResponse response) throws IOException {
        var content = inventoryStocktakeImportService.buildTemplate(warehouseId);
        SmartResponseUtil.setDownloadFileHeader(response, "盘点导入模板-" + warehouseId + ".xlsx", (long) content.length);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    /**
     * 导入填好实盘量的 Excel → 新建草稿盘点单。
     *
     * <p>
     * <b>只建草稿，不改动库存</b>；整批校验或快照核验任一不过即整批拒绝、不落库。 {@code Idempotency-Key} 让响应丢失后的同请求重试不产生第二张草稿。
     */
    @PostMapping("/import")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_IMPORT)
    @OperateLog
    public ResponseDTO<
            InventoryStocktakeImportResultVO> importStocktake(@RequestParam MultipartFile file,
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
                    throws Exception {
        if (file.isEmpty()) {
            return ResponseDTO.userErrorParam("导入文件不能为空");
        }
        var name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return ResponseDTO.userErrorParam("仅支持 .xlsx 文件");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            return ResponseDTO.userErrorParam("导入文件不能超过 10 MiB");
        }
        var security = securityFileService.checkFile(file);
        if (!security.getOk()) {
            return ResponseDTO.error(security);
        }
        return ResponseDTO.ok(inventoryStocktakeImportService.importFile(file, idempotencyKey));
    }

    /**
     * 改草稿（仅 DRAFT）；会重新快照账面量。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> update(@PathVariable("id") Long stocktakeId, @Valid @RequestBody InventoryStocktakeAddForm form) {
        inventoryStocktakeService.update(stocktakeId, form);
        return ResponseDTO.ok();
    }

    /**
     * 确认盘点：差异转盘盈 / 盘亏流水并调整余额。
     *
     * <p>
     * 这是本模块唯一会改变库存的端点，失败整单回滚，不存在「盘一半」。
     */
    @PostMapping("/confirm/{id}")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_CONFIRM)
    @OperateLog
    public ResponseDTO<
            String> confirm(@PathVariable("id") Long stocktakeId) {
        inventoryStocktakeService.confirm(stocktakeId);
        return ResponseDTO.ok();
    }

    /**
     * 取消草稿（不产生任何库存影响）。
     */
    @PostMapping("/cancel/{id}")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> cancel(@PathVariable("id") Long stocktakeId) {
        inventoryStocktakeService.cancel(stocktakeId);
        return ResponseDTO.ok();
    }

    /**
     * 删除草稿（逻辑删）。已确认的单不可删。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission(InventoryPermission.STOCKTAKE_DELETE)
    @OperateLog
    public ResponseDTO<
            String> delete(@PathVariable("id") Long stocktakeId) {
        inventoryStocktakeService.delete(stocktakeId);
        return ResponseDTO.ok();
    }
}
