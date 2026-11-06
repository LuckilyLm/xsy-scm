# 数据库回归资源

- permission_upgrade.sql仅在名称以_it结尾的隔离库执行，验证既有主键占用、角色授权保留、序列只前移和重复执行。
- 入口为tools/test_install_baseline.py，显式设置XSY_PERMISSION_PG_IT=1才连接本地Docker中的PostgreSQL；不连接业务数据库。
- 被测SQL直接读取正式V1003资源，不复制一份修改过的迁移来代替验证。
