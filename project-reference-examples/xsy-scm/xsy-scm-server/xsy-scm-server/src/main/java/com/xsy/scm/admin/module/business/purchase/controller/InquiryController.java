package com.xsy.scm.admin.module.business.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQuoteForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryStatusForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryCompareVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryDetailVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryVO;
import com.xsy.scm.admin.module.business.purchase.service.InquiryService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购询价报价 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class InquiryController {

    @Resource
    private InquiryService inquiryService;

    @Operation(summary = "分页查询询价单 @author xsy-scm")
    @PostMapping("/inquiry/query")
    @SaCheckPermission("inquiry:query")
    public ResponseDTO<PageResult<InquiryVO>> query(@RequestBody @Valid InquiryQueryForm queryForm) {
        return inquiryService.query(queryForm);
    }

    @Operation(summary = "查询询价单详情 @author xsy-scm")
    @GetMapping("/inquiry/get/{inquiryId}")
    @SaCheckPermission("inquiry:query")
    public ResponseDTO<InquiryDetailVO> detail(@PathVariable Long inquiryId) {
        return inquiryService.detail(inquiryId);
    }

    @Operation(summary = "询价方案对比（平均价/中位价） @author xsy-scm")
    @PostMapping("/inquiry/compare/{inquiryId}")
    @SaCheckPermission("inquiry:query")
    public ResponseDTO<InquiryCompareVO> compare(@PathVariable Long inquiryId) {
        return inquiryService.compare(inquiryId);
    }

    @Operation(summary = "添加询价单 @author xsy-scm")
    @PostMapping("/inquiry/add")
    @SaCheckPermission("inquiry:add")
    public ResponseDTO<String> add(@RequestBody @Valid InquiryAddForm addForm) {
        return inquiryService.add(addForm);
    }

    @Operation(summary = "更新询价单 @author xsy-scm")
    @PostMapping("/inquiry/update")
    @SaCheckPermission("inquiry:update")
    public ResponseDTO<String> update(@RequestBody @Valid InquiryUpdateForm updateForm) {
        return inquiryService.update(updateForm);
    }

    @Operation(summary = "供应商报价 @author xsy-scm")
    @PostMapping("/inquiry/quote")
    @SaCheckPermission("inquiry:quote")
    public ResponseDTO<String> quote(@RequestBody @Valid InquiryQuoteForm quoteForm) {
        return inquiryService.quote(quoteForm);
    }

    @Operation(summary = "变更询价单状态（完成/取消） @author xsy-scm")
    @PostMapping("/inquiry/changeStatus")
    @SaCheckPermission("inquiry:changeStatus")
    public ResponseDTO<String> changeStatus(@RequestBody @Valid InquiryStatusForm statusForm) {
        return inquiryService.changeStatus(statusForm);
    }

    @Operation(summary = "删除询价单 @author xsy-scm")
    @GetMapping("/inquiry/delete/{inquiryId}")
    @SaCheckPermission("inquiry:delete")
    public ResponseDTO<String> delete(@PathVariable Long inquiryId) {
        return inquiryService.delete(inquiryId);
    }

    @Operation(summary = "批量删除询价单 @author xsy-scm")
    @PostMapping("/inquiry/batchDelete")
    @SaCheckPermission("inquiry:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return inquiryService.batchDelete(idList);
    }
}
