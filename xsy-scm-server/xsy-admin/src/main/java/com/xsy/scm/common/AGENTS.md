# SCM公共后端能力

- 当前数据库规则以根目录docs/decisions.md为准。ScmServiceValidationAspect复用domain.form的Jakarta约束，必须在事务及写入前拒绝违规参数；不在切面解释SQL或复制领域状态机。
- 追加记录的BaseMapper统一继承AppendOnlyMapper，新增批量覆盖、upsert或删除入口时必须保留拒绝语义。自定义SQL仍由对应领域约束及源码约定检查保护。
- common不能依赖业务域；参数对象只按通用约定识别，领域规则归属各Service/Policy。
