package com.xsy.scm.base.module.support.table;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.repeatsubmit.annoation.RepeatSubmit;
import com.xsy.scm.base.module.support.table.domain.TableColumnUpdateForm;
import org.springframework.web.bind.annotation.*;

/**
 * 表格自定义列（前端用户自定义表格列，并保存到数据库里）
 *
 */
@RestController
@Tag(name = SwaggerTagConst.Support.TABLE_COLUMN)
public class TableColumnController extends SupportBaseController {

    @Resource
    private TableColumnService tableColumnService;

    @Operation(summary = "修改表格列")
    @PostMapping("/tableColumn/update")
    @RepeatSubmit
    public ResponseDTO<String> updateTableColumn(@RequestBody @Valid TableColumnUpdateForm updateForm) {
        return tableColumnService.updateTableColumns(XsyRequestUtil.getRequestUser(), updateForm);
    }

    @Operation(summary = "恢复默认（删除）")
    @GetMapping("/tableColumn/delete/{tableId}")
    @RepeatSubmit
    public ResponseDTO<String> deleteTableColumn(@PathVariable Integer tableId) {
        return tableColumnService.deleteTableColumn(XsyRequestUtil.getRequestUser(), tableId);
    }

    @Operation(summary = "查询表格列")
    @GetMapping("/tableColumn/getColumns/{tableId}")
    public ResponseDTO<String> getColumns(@PathVariable Integer tableId) {
        return ResponseDTO.ok(tableColumnService.getTableColumns(XsyRequestUtil.getRequestUser(), tableId));
    }
}
