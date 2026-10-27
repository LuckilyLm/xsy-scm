package com.xsy.scm.businesscategory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryDeleteForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryTreeQueryForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryTreeVO;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryVO;
import com.xsy.scm.businesscategory.permission.BusinessCategoryPermission;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/scm/business-category")
@Tag(name = "SCM 客户供应商分类")
@RequiredArgsConstructor
public class BusinessCategoryController {

    private final BusinessCategoryService businessCategoryService;

    @PostMapping("/tree")
    @SaCheckPermission(BusinessCategoryPermission.QUERY)
    public ResponseDTO<List<BusinessCategoryTreeVO>> tree(@Valid @RequestBody BusinessCategoryTreeQueryForm form) {
        return ResponseDTO.ok(businessCategoryService.tree(form.getScope()));
    }

    @GetMapping("/{categoryId}")
    @SaCheckPermission(BusinessCategoryPermission.QUERY)
    public ResponseDTO<BusinessCategoryVO> detail(@PathVariable Long categoryId) {
        return ResponseDTO.ok(businessCategoryService.detail(categoryId));
    }

    @PostMapping("/add")
    @SaCheckPermission(BusinessCategoryPermission.ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody BusinessCategoryAddForm form) {
        return ResponseDTO.ok(businessCategoryService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(BusinessCategoryPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody BusinessCategoryUpdateForm form) {
        businessCategoryService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(BusinessCategoryPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody BusinessCategoryDeleteForm form) {
        businessCategoryService.delete(form);
        return ResponseDTO.ok();
    }
}
