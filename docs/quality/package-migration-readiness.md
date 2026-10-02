# SCM 迁包工具说明

Q1 包迁移已完成；此文件保留为 `tools/quality/package_migration_readiness.py` 的文档依赖，不是待执行计划。

- 正式命名空间为 `com.xsy.scm`，旧 SCM 包应为空。
- [迁包清单](q1-package-migration-manifest.json)记录迁移前每个文件的精确归属，只读保留；不能用相同文件数量代替文件身份核对，也不得重录已完成迁移的基线。
- 工具覆盖源码、Mapper XML、Spring/MyBatis 扫描与反射字符串；迁移前后必须确认 Checkstyle、Spotless、ArchUnit 和源码守卫都识别正式包。
- `--force` 不允许在迁移前置条件不满足时覆盖既有清单。
- Spotless 增量检查没有覆盖文件时，不得凭退出成功推断全量格式通过；应准确记录覆盖范围或 INCOMPLETE。
- 当前持续规则与最近验证记录统一见[工程质量基线](java-code-quality-remediation-plan.md)。旧探针过程、提交清单和阶段审计从 Git 历史追溯。
