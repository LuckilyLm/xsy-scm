# 运行配置

- 数据库与schema统一为xsy，连接使用XSY_DB_URL、XSY_DB_USERNAME、XSY_DB_PASSWORD。原资料通过备份、重建和表数据导入保留，不自动切回其他schema或复用原迁移历史。

- dev、test、pre、prod只加载classpath:db/migration/modules中的模块初始化与后续增量，不再加载退役历史目录。旧库先显式核对并切换基线。
- 保持baseline-on-migrate关闭、validate-on-migrate开启及clean-disabled开启；不要为绕过历史校验修改迁移记录。
