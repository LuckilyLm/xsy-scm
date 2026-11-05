# PostgreSQL测试支持

- 沿用PgITDatabase的隔离测试库规则，路径由PgITPaths提供，不能硬编码个人目录。
- PgITMigrationHistory只认可有checksum的成功SQL，或指定1000.24切换标记且原历史表仍存在；普通手工baseline不能证明已经安装。具体表、约束和默认项仍由业务用例断言。
- LegacyMigrationSql固定读取已核对Git提交中的原V文件，用于历史回放；缺Git或对象时明确失败，不跳过测试，不重新向制品复制退役SQL。
