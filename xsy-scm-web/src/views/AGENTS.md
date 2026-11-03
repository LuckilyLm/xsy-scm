# 管理后台页面

- 表格使用 `size="small"` 与 `bordered`，基础视觉参考 `support/file/file-list.vue` 和根目录 `DESIGN.md`。
- 已有服务端分页保留正式接口的页码、条数与总数；`pagination="false"` 不代表缺分页，先检查页面或父组件的独立分页器。
- 完整数组的可增长只读列表可使用 `components/support/local-table`；必须核实接口提供完整结果，不能给已截断的数组伪造总数。
- 菜单树、部门树、分类树、编辑/批量确认与打印表格保持完整上下文；游标加载继续使用原有加载入口。
- 商品图片中心选择区使用商品查询接口的服务端分页，不能恢复成固定只取前 50 条。
- 导出按钮使用 `type="primary"` 的文字形式，保留原有权限、加载、禁用与事件；工具栏间距交给公共主题。
- 单一表格操作优先用 `smart-query-layout` 与查询区并排；已在查询表单内的操作可用 `smart-query-table-actions` 靠右。无查询条件时用 `data-toolbar-layout="single"` 单独一行右对齐。
