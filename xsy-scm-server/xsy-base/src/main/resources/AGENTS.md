# 运行配置

- dev、test、pre、prod只加载classpath:db/migration/modules中的模块初始化与后续增量，不再加载退役历史目录。旧库先显式核对并切换基线。
- 保持baseline-on-migrate关闭、validate-on-migrate开启及clean-disabled开启；不要为绕过历史校验修改迁移记录。
