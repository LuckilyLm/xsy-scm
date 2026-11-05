# 管理服务工程

- 当前Flyway只扫描`classpath:db/migration/modules`，24份最终初始化及后续增量放在该目录；旧库先用显式工具核对并切换基线。
- maven-jar-plugin排除旧平铺迁移与migration-history，防止增量构建残留资源重新进入制品；不能扩大通配符误排除modules内的正式SQL。
- 历史迁移回放测试依赖固定Git对象，不重新打包退役SQL为测试资源。运行测试和构建仍遵守根AGENTS授权约定。
