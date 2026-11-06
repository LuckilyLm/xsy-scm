package com.xsy.scm.admin.module.system.support;

import com.xsy.scm.base.module.support.file.permission.FilePermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.file.domain.form.FileQueryForm;
import com.xsy.scm.base.module.support.file.domain.vo.FileVO;
import com.xsy.scm.base.module.support.file.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件服务
 *
 */
@RestController
@Tag(name = SwaggerTagConst.Support.FILE)
public class AdminFileController extends SupportBaseController {

    @Resource
    private FileService fileService;

    @Operation(summary = "分页查询")
    @PostMapping("/file/queryPage")
    @SaCheckPermission(FilePermission.QUERY)
    public ResponseDTO<PageResult<FileVO>> queryPage(@RequestBody @Valid FileQueryForm queryForm) {
        return ResponseDTO.ok(fileService.queryPage(queryForm));
    }

}
