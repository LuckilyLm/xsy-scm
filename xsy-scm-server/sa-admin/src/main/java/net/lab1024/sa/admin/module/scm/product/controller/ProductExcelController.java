package net.lab1024.sa.admin.module.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductExportExcelVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImportResultVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSpuVO;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService.ImportMode;
import net.lab1024.sa.admin.module.scm.product.service.ProductQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartExcelUtil;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/product")
@Tag(name = "SCM 商品导入导出")
public class ProductExcelController {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int EXPORT_MAX_ROWS = 100000;
    /** 更新导入改写既存商品，除导入权外还要求商品编辑权；权限串与 ProductController 保持一致。 */
    private static final String PRODUCT_UPDATE_PERMISSION = "scm:product:update";

    private final ProductImportService importService;
    private final ProductQueryService query;
    private final SecurityFileService securityFileService;

    @GetMapping("/import/template")
    @SaCheckPermission("scm:product:import")
    public void template(@RequestParam(required = false, defaultValue = "CREATE") ImportMode mode,
                         HttpServletResponse response) throws IOException {
        // 更新模板带定位键、会改写既存商品，因此下载模板也要编辑权
        if (mode == ImportMode.UPDATE) StpUtil.checkPermission(PRODUCT_UPDATE_PERMISSION);
        var content = importService.buildTemplate(mode);
        SmartResponseUtil.setDownloadFileHeader(response,
                (mode == ImportMode.UPDATE ? "商品更新导入模板" : "商品导入模板") + ".xlsx", (long) content.length);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @PostMapping("/import")
    @SaCheckPermission("scm:product:import")
    @OperateLog
    public ResponseDTO<ProductImportResultVO> importProducts(@RequestParam MultipartFile file,
                                                            @RequestParam(required = false, defaultValue = "CREATE") ImportMode mode) throws Exception {
        // 更新模式直接改写既存商品，导入权不等于编辑权，必须服务端兜底
        if (mode == ImportMode.UPDATE) StpUtil.checkPermission(PRODUCT_UPDATE_PERMISSION);
        if (file.isEmpty()) return ResponseDTO.userErrorParam("导入文件不能为空");
        var name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            return ResponseDTO.userErrorParam("仅支持 .xlsx 文件");
        if (file.getSize() > MAX_FILE_SIZE) return ResponseDTO.userErrorParam("导入文件不能超过 10 MiB");
        var security = securityFileService.checkFile(file);
        if (!security.getOk()) return ResponseDTO.error(security);
        return ResponseDTO.ok(importService.importFile(file, mode));
    }

    @PostMapping("/export")
    @SaCheckPermission("scm:product:export")
    @OperateLog
    public void export(@RequestBody ProductSpuQueryForm form, HttpServletResponse response) throws IOException {
        form.setPageNum(1L);
        form.setPageSize((long) EXPORT_MAX_ROWS);
        PageResult<ProductSpuVO> page = query.query(form);
        var rows = new ArrayList<ProductExportExcelVO>();
        for (var spu : page.getList()) rows.addAll(flatten(spu));
        SmartExcelUtil.exportExcel(response, "商品档案导出.xlsx", "商品", ProductExportExcelVO.class, rows);
    }

    private List<ProductExportExcelVO> flatten(ProductSpuVO spu) {
        var tagNames = spu.getTags() == null ? "" : spu.getTags().stream()
                .map(t -> t.getName()).filter(java.util.Objects::nonNull).collect(Collectors.joining(","));
        var skus = spu.getSkuList() == null || spu.getSkuList().isEmpty() ? List.<ProductSkuVO>of() : spu.getSkuList();
        var out = new ArrayList<ProductExportExcelVO>();
        for (var sku : skus) {
            var vo = new ProductExportExcelVO();
            vo.setSpuId(text(spu.getSpuId()));
            vo.setSpuVersion(text(spu.getVersion()));
            vo.setSkuId(text(sku.getSkuId()));
            vo.setSkuVersion(text(sku.getVersion()));
            vo.setSpuCode(spu.getSpuCode());
            vo.setSpuName(spu.getName());
            vo.setAlias(spu.getAlias());
            vo.setCategoryPath(spu.getCategoryPath());
            vo.setMnemonicCode(spu.getMnemonicCode());
            vo.setBrandName(spu.getBrandName());
            vo.setOrigin(spu.getOrigin());
            vo.setStorageMethod(spu.getStorageMethod());
            vo.setMasterStatus(spu.getMasterStatus());
            vo.setSpuStatus(spu.getStatus());
            vo.setTagNames(tagNames);
            vo.setSkuCode(sku.getSkuCode());
            vo.setBarcode(sku.getBarcode());
            vo.setSpecName(sku.getSpecName());
            vo.setSaleUnit(sku.getSaleUnit());
            vo.setProductType(sku.getProductType());
            vo.setMarketPrice(sku.getMarketPrice() == null ? "" : sku.getMarketPrice().toPlainString());
            vo.setSkuStatus(sku.getStatus());
            vo.setDefaultFlag(Boolean.TRUE.equals(sku.getDefaultFlag()) ? "是" : "否");
            vo.setSortOrder(sku.getSortOrder() == null ? "" : String.valueOf(sku.getSortOrder()));
            out.add(vo);
        }
        if (out.isEmpty()) out.add(baseRow(spu, tagNames));
        return out;
    }

    private ProductExportExcelVO baseRow(ProductSpuVO spu, String tagNames) {
        var vo = new ProductExportExcelVO();
        vo.setSpuId(text(spu.getSpuId()));
        vo.setSpuVersion(text(spu.getVersion()));
        vo.setSpuCode(spu.getSpuCode());
        vo.setSpuName(spu.getName());
        vo.setAlias(spu.getAlias());
        vo.setCategoryPath(spu.getCategoryPath());
        vo.setMnemonicCode(spu.getMnemonicCode());
        vo.setBrandName(spu.getBrandName());
        vo.setOrigin(spu.getOrigin());
        vo.setStorageMethod(spu.getStorageMethod());
        vo.setMasterStatus(spu.getMasterStatus());
        vo.setSpuStatus(spu.getStatus());
        vo.setTagNames(tagNames);
        return vo;
    }

    /** 定位键列取空即为空串：SKU 缺失的兜底行没有 SKU 定位键，不能成为可导入的更新行。 */
    private String text(Number value) {
        return value == null ? "" : String.valueOf(value);
    }
}
