# 项目文档

本目录只保存当前项目必须维护的少量记录：

- [progress.md](progress.md)：按日期追加的交付进度、验证状态和待办。
- [decisions.md](decisions.md)：当前仍然有效的项目边界和技术决策。

业务需求、旧系统语义、页面参考和历史方案以只读目录
[`project-reference-examples/xsy-scm/`](../project-reference-examples/xsy-scm/) 为主要参考来源。
当前代码行为以 `AGENTS.md`、`SMARTADMIN_REFERENCE_RULES.md` 和实现为准；参考项目不能覆盖当前项目规则。

## 维护规则

- 新进度追加到 `progress.md`，不为每个波次重复创建一组设计、审计、批准和验收文件。
- 新决策追加到 `decisions.md`，保留日期、范围和未决事项；不改写已经发生的进度记录。
- 不在本目录复制参考项目的需求、UI 指导、截图或旧技术方案。
- 迁移校验、生成物和本机运行信息不作为叙事文档维护；需要时从 Git、代码和部署配置重新核对。
- 删除或移动文档后同步更新根目录说明、`AGENTS.md`、迁移注释和部署说明中的链接。

当前实现状态见 [progress.md](progress.md)。
