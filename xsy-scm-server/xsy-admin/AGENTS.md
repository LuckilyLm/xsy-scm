# 管理服务工程

- Maven模块为xsy-admin，入口com.xsy.scm.admin.XsyApplication。系统管理代码位于com.xsy.scm.admin，供应链业务保持com.xsy.scm下原有业务目录，统一扫描com.xsy.scm。
- 公共能力由xsy-base提供；修改文件名、类名或资源路径时同步核对XML、反射、任务配置和测试读取路径，不能只调整Java导入。

- 当前Flyway只扫描`classpath:db/migration/modules`，24份最终初始化及后续增量放在该目录；旧库先用显式工具核对并切换基线。
- maven-jar-plugin排除旧平铺迁移与migration-history，防止增量构建残留资源重新进入制品；不能扩大通配符误排除modules内的正式SQL。
- 重新执行历史迁移测试依赖固定Git对象，不重新打包退役SQL为测试资源。运行测试和构建仍遵守根AGENTS授权约定。
