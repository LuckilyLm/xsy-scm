package com.xsy.scm.base.module.support.datatracer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.datatracer.domain.form.DataTracerQueryForm;
import com.xsy.scm.base.module.support.datatracer.domain.vo.DataTracerVO;
import com.xsy.scm.base.module.support.datatracer.service.DataTracerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据变动记录
 *
 */
@Tag(name = SwaggerTagConst.Support.DATA_TRACER)
@RestController
public class DataTracerController extends SupportBaseController {

    @Resource
    private DataTracerService dataTracerService;

    @Operation(summary = "分页查询业务操作日志 -")
    @PostMapping("/dataTracer/query")
    public ResponseDTO<PageResult<DataTracerVO>> query(@Valid @RequestBody DataTracerQueryForm queryForm) {
        return dataTracerService.query(queryForm);
    }
}
