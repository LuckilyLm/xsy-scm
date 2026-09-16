# SmartAdmin × PostgreSQL 收口报告（C 侧 PG 资产对齐 + mapper MySQL 残留清理）

日期：2026-09-16
范围：C 侧已交付的 MySQL→PostgreSQL 转换资产与当前 V2 SmartAdmin 系统 schema 的对账；当前正式 mapper 的 MySQL 残留全量扫描与修复；SmartAdmin 原生功能的真实 PostgreSQL 连通性验证；D-1 的 schema 根因修复（V12）。
**本阶段不进入 W4。**

---

## 0 最终状态

```text
SmartAdmin PostgreSQL Closure = GO
active runtime MySQL dependency = 0
active Mapper MySQL dialect    = 0
system schema coverage         = 100%
feature probe                  = 53/53 PASS
W1/W2/W3 regression            = PASS
V1-V11 unchanged
V12 frozen
```

保留的非阻塞遗留（**本次刻意未扩大修改面**）：

1. `SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` —— dead code（语句本身非法，两端都非法；无调用方）。
2. `t_employee.login_pwd VARCHAR(100)` —— 余量较小（实测 argon2id 哈希 97 字符，剩 3 字符），当前无 bug。
3. 未使用的 `mysql-connector-j.version` Maven 属性 —— 无任何 dependency 引用，不进入运行时 classpath。

---

## 0.1 结论摘要（明细）

| 项 | 结论 |
|---|---|
| D1 结构 diff | C `01-系统基础表.sql` 44 表 vs V2 `V1–V5` 42 表。**SAME 32 / INTENTIONAL_V2_DIFFERENCE 12 / POSSIBLE_DEFECT 0**。`t_category`、`t_goods` 为 SmartAdmin demo 业务表，当前源码零引用（实体、mapper、菜单、前端均无），维持 W0 判定不迁。**未把「44 vs 42」报告为缺表。**（收口后 `t_oa_enterprise_employee` 由 SAME 转为 INTENTIONAL——即 D-1 方案 A 的直接结果，见 §9。） |
| D2 mapper 全量扫描 | 48 个 src mapper XML + 代码生成器模板 + 4 套环境配置。静态扫描发现 **1 处真实残留（驼峰列名）+ 4 处可达/潜在缺陷**，动态探针又发现 **2 处日期参数类型缺陷**，另修 **2 处 MySQL 痕迹再生源头**（代码生成器），合计 **N1–N9 共 9 处，全部已修**。修复后 active SQL 中 MySQL 专有函数与语法命中数 = **0**。 |
| D3 真实 PG 测试 | 静态层：新增 `SmartAdminMapperPgValidationIT`，把 **1314 条** mapper 语句的最终 SQL 交给真实 PostgreSQL `PREPARE` 解析，**failed=0**（**仅 1 条**已知失败进入显式棘轮基线，见 §3.4）。**动态层（关键）**：新增 `tools/smartadmin_feature_probe.mjs` 打真实后端 + 真实 PG，**它抓到了静态 `PREPARE` 抓不到的一整类缺陷 N8/N9**（日期列 vs String 参数），见 §4.2。 |
| D4 运行时痕迹 | `jdbc:mysql` = 0；`mysql-connector` 依赖 = 0（仅父 pom 一个**未被引用**的 version 属性）；`DbType.MYSQL` 仅存在于 2 处说明性注释；`MySql/Mysql` 命中全部为注释或非 SQL 标识（`VelocityEngine`、正则字符类、JS 模板字符串）；`3306` 命中全部为行政区划码。**active runtime 归零。** |
| D5 功能验证 | 用 C 01 的 44 表清单反查当前源码：**42/44 表有活跃实体+mapper 绑定**，仅 `t_category`/`t_goods` 零引用。SmartAdmin 原生功能逐项打真实后端 + 真实 PostgreSQL：**53 项探针全部通过（53/53），failed=0，unmappedPath=0**；其中 `POST /oa/enterprise/employee/queryPage` 在 V12 后由 FAIL 转 `code=0`（见 §6.2）。 |
| D6 后续资产登记 | `02–08` 已登记为后续业务迁移参考资产（Order→04 / Purchase→05 / Inventory→06 / Finance→08 / P1→07），本阶段未迁入正式库，见 §7。 |
| 冻结 | `V1–V11` **未改动**：哈希与 `docs/architecture/v3-applied-migrations.sha256` 逐字节一致。**新增 `V12__sa_oa_enterprise_employee_bigint.sql`**（D-1 方案 A），首次成功应用后计算 SHA-256 并写入 `docs/architecture/pg-closure-applied-migrations.sha256`（V1–V12 全集）。Flyway `flyway_schema_history` **12 条全部 `success = t`**，`migrate` / `validate` PASS。 |
| 需用户决策 | **0 项**。D-1 已由用户批准**方案 A**，本次以 V12 完成 schema 根因修复并闭环（见 §9）。 |

**修复清单（N1–N9，共 9 项，全部为 mapper / 模板层）**：
N1–N5 来自静态扫描（§3.3），N6–N7 是 MySQL 痕迹的再生源头（代码生成器，§3.3），
**N8–N9 由动态功能探针发现**（§4.2）——这是本次收口最重要的增量发现。

**另加 1 项 schema 层修复（D-1 方案 A，用户批准）**：`V12` 把 `t_oa_enterprise_employee` 的两个关联列
改为 `BIGINT`（§9）。它与 N1–N9 的区别是：N1–N9 全在 mapper/模板层、零 DDL；V12 是**经用户明确批准**的
DDL 变更，且只作用于**空表**、只加新 migration，**不回改 V1–V11**。

---

## 1 边界与只读声明

- `project-reference-examples/xsy-scm/postgresql/**` **严格只读**：本阶段未执行其任何语句到 `xsy_v2`，未复制为正式 Flyway migration。
- 只使用 `01-系统基础表.sql` 作为结构 diff 的参考面；`99-初始化数据.sql`、`all-in-one.sql` 仅作历史/参考，**未执行**。
- 当前 V2 继续保持 clean SmartAdmin seed，**未导入** C 的 demo / runtime 数据。
- `02–08` 本阶段**未迁入**正式数据库。
- Product / Customer / Pricing（W1–W3）**未被 C 旧表覆盖**；`V1–V11` 完全冻结（哈希见 §8.1）。
- `project-reference-examples/xsy-scm/**` 全程**零修改**（`git status --porcelain -- project-reference-examples/` 为空）。
- 本阶段新增的唯一 DDL 是 `V12`（D-1 方案 A，用户批准）；不采用 mapper `CAST` workaround。
- 本阶段不再做通用 MySQL DDL → PG 转换。

---

## 2 D1：系统表结构 diff

### 2.1 对账口径

- C 侧：`project-reference-examples/xsy-scm/postgresql/01-系统基础表.sql`，44 张 SmartAdmin 框架表。
- V2 侧：**live PostgreSQL 的 `xsy_v2` schema**（= `V1–V5` 已落库、已验证的真实结构），而不是读 migration 文本。
  - `xsy_v2` 实测 57 张 BASE TABLE = 42 张 `t_*` 系统表 + 14 张 SCM 表 + `flyway_schema_history`。
- 比较维度：`table / column / type / nullable / default / identity / primary key / unique / index`。
- 工具：`tools/verify_system_schema_diff.py`（可重跑，退出码非 0 表示存在 `POSSIBLE_DEFECT`）。
- 类型归一：C 的 `tinyint/tinyint(1) → SMALLINT` 与 V2 的 `tinyint(1) 0/1 → BOOLEAN`、其他 `tinyint → SMALLINT` **按类型族等价处理**，不把有意的 BOOLEAN 取舍报成差异。

### 2.2 结果

```
C 参考表数 = 44   V2 实测 t_* 表数 = 42
SAME = 32   INTENTIONAL_V2_DIFFERENCE = 12   POSSIBLE_DEFECT = 0
```

`SAME`（32 张，逐列类型/可空/默认/identity/主键/唯一/索引全部一致）：
`t_change_log`、`t_code_generator_config`、`t_data_tracer`、`t_dict`、`t_feedback`、`t_file`、`t_heart_beat_record`、`t_help_doc`、`t_help_doc_catalog`、`t_help_doc_relation`、`t_help_doc_view_record`、`t_login_log`、`t_mail_template`、`t_message`、`t_notice`、`t_notice_type`、`t_notice_view_record`、`t_notice_visible_range`、`t_oa_bank`、`t_oa_enterprise`、`t_oa_invoice`、`t_password_log`、`t_position`、`t_reload_item`、`t_role`、`t_role_employee`、`t_role_menu`、`t_serial_number`、`t_serial_number_record`、`t_smart_job`、`t_smart_job_log`、`t_table_column`

`INTENTIONAL_V2_DIFFERENCE`（12 张）：

| 表 | 差异 | 裁决 |
|---|---|---|
| `t_category` | 整表不迁 | SmartAdmin demo 业务表；当前源码零引用（`@TableName`/mapper/菜单/前端全无）。V2 对应能力由 W1 的 `product_category` 承担。 |
| `t_goods` | 整表不迁 | 同上；V2 由 `product_spu` / `product_sku` 承担。 |
| `t_config` | `update_time` C=NULL → V2=NOT NULL；V2 增 `uk_t_config_key` | V2 收紧 + 自然键唯一。写入路径由 `MybatisPlusFillHandler.insertFill/updateFill` 保证 `updateTime` 必被赋值；`t_config` 种子 2 行 `config_key` 无重复（`level3_protect_config` / `super_password`）。 |
| `t_department` | `sort` C=无默认 → V2=`DEFAULT 0` | 等价于 MySQL 非严格模式的隐式默认值；`DepartmentService.addDepartment` 直接插表单值，缺省时由 DB 兜底。 |
| `t_dict_data` | V2 增 `idx_t_dict_data_dict_id` | 子表外键列索引；V1 头部规则 7「UNIQUE KEY / INDEX → 显式命名约束与索引」。 |
| `t_employee` | `disabled_flag`/`deleted_flag` 补 `DEFAULT FALSE`；`login_pwd` C=`varchar(255)` V2=`varchar(100)`；V2 增 2 个索引 | 默认值补齐等价 MySQL 隐式默认；`login_pwd` **V2 与上游 SmartAdmin v3.31 DDL 完全一致**（C 侧 255 来自线上库 alter，非 v3.31 基线）。实测 argon2id 哈希 97 字符，100 有 3 字符余量，见 §2.3。 |
| `t_login_fail` | `create_time`/`update_time` C=NULL → V2=NOT NULL | 同 `t_config`，由 `MybatisPlusFillHandler` 保证。 |
| `t_menu` | V2 增 `idx_t_menu_parent_id`、`idx_t_menu_menu_type` | 菜单树与按类型查询的支撑索引。 |
| `t_oa_enterprise_employee` | `enterprise_id`/`employee_id` C=`varchar(100)` → V2=`bigint`（**V12**） | **D-1 方案 A（用户批准）**。Java 实体两列都是 `Long`，且 `t_oa_enterprise.enterprise_id` / `t_employee.employee_id` 都是 `bigint`。C 侧的 `varchar(100)` 来自上游 MySQL 线上库，MySQL 在 varchar↔bigint 比较/JOIN 时隐式转数值，PostgreSQL 不会。故从 schema 根因修复，而非在 mapper 里加 `CAST`（后者会让 `idx_*` 与唯一键失效）。详见 §9。 |
| `t_operate_log` | V2 增 `idx_t_operate_log_create_time`、`idx_t_operate_log_user(operate_user_id, operate_user_type)` | **V4 文件头部已显式声明**。 |
| `t_reload_result` | V2 增 `idx_t_reload_result_tag(tag, create_time)` | **V4 文件头部已显式声明**（上游无任何索引而唯一查询是 `where tag = ?`）。 |
| `t_role_data_scope` | V2 增 `idx_t_role_data_scope_role_id` | 角色数据范围查询支撑索引。 |

### 2.3 两处需要记录的观察项（不构成缺陷，不阻塞）

1. **`t_employee.login_pwd VARCHAR(100)` 余量偏小。** 上游 SmartAdmin v3.31 与 V2 都是 `VARCHAR(100)`，实测库内 argon2id 哈希 **97 字符**，只剩 3 字符余量。若将来调整 `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` 的参数（salt/hash 长度），写入会直接失败。当前**未观察到任何 bug**，因此按「最终类型以上游 v3.31 Java field type / Mapper contract / V1–V5 已验证行为为准」保持 `VARCHAR(100)`，不自行加宽。建议在将来确需调整 Argon2 参数时，用一个**新的 migration（V13+）**一并加宽（非破坏性 ALTER）。**本次刻意不做**——避免扩大改动面（`V12` 已被 D-1 占用）。
2. **`t_config` 唯一约束 `uk_t_config_key` 属 V2 新增强化**，上游无此约束。已确认：种子无重复、`ConfigService` 以 `config_key` 为自然键读写，故不构成风险。记录在此是为了让该约束的出处可追溯（它不在 V1 头部规则 1–7 的明文列举内）。

---

## 3 D2：mapper 全量扫描与分类

### 3.1 扫描范围与方法

| 扫描面 | 数量 |
|---|---|
| `xsy-scm-server/**/mapper/**/*.xml`（src，排除 `target/`） | 48 |
| 代码生成器模板 `code-generator-template/**` | 21 |
| 后端 `.java` / `.yaml` / `.properties` / `.sql` / `.vm`（src） | 829 |
| 4 套环境配置（dev/pre/prod/test） | 8 |

检索词：`IFNULL`、`INSTR`、`DATE_FORMAT`、`STR_TO_DATE`、`GROUP_CONCAT`、`FIND_IN_SET`、`JSON_EXTRACT`、`JSON_UNQUOTE`、`JSON_CONTAINS`、`INSERT IGNORE`、`ON DUPLICATE KEY UPDATE`、`LAST_INSERT_ID`、`LIMIT x,y`、`FIELD(`、`CAST AS SIGNED`、`UNIX_TIMESTAMP`、`FROM_UNIXTIME`，另加反引号、`SUBSTRING_INDEX`、`SQL_CALC_FOUND_ROWS`、`FROM DUAL`、`TINYINT`、`AUTO_INCREMENT`、`ENGINE=`、`utf8mb4`。

分类口径：
- `ALREADY_PG` — 已转换且经真实 PG 解析通过。
- `NEED_FIX` — 可被 PG 拒绝（语法/函数/标识符），且当前调用链可达。
- `DEAD_CODE` — 语句本身非法（**在 MySQL 下同样非法**），但当前调用链不可达。
- `COMMENT_ONLY` — 仅出现在注释/文档字符串中。

### 3.2 分类结果

| 分类 | 数量 | 说明 |
|---|---|---|
| `ALREADY_PG` | **1311 条语句**（收口后 **1314**） | W0 的 `tools/pg_convert_mapper_xml.py` 已把 `INSTR → STRPOS(...) > 0`、`DATE_FORMAT(a,'%Y-%m-%d') → CAST(a AS DATE)` 机械转换完毕。经真实 PG `PREPARE` 逐条验证通过（§4）。收口时 1311；V12 修复 D-1 后 3 条原 `BLOCKED_ON_SCHEMA_DECISION` 语句转入本类，故最终 **1314**。 |
| `NEED_FIX` | **9 处** | 见 §3.3，全部已修。其中 N1–N5 由静态扫描发现，N6–N7 为 MySQL 痕迹再生源头，**N8–N9 由 §4.2 的动态功能探针发现**。 |
| `DEAD_CODE` | **1 处** | `SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate`，见 §3.4。 |
| `COMMENT_ONLY` | 其余全部命中 | 反引号命中全部为 JS 模板字符串 / ASCII banner / 正则字符类 / 中文注释；`TINYINT`/`AUTO_INCREMENT`/`DbType.MYSQL`/`MySql` 命中全部为 V1 头部转换规则说明或「原基线为 MySQL」的说明性注释。 |

**修复后复扫：active SQL 中上述 MySQL 专有函数与语法的命中数 = 0。**

### 3.3 NEED_FIX 明细与修改

| # | 位置 | 问题 | 证据 | 处理 |
|---|---|---|---|---|
| N1 | `sa-admin/.../oa/notice/NoticeMapper.xml` `queryEmployeeNotViewNotice` | `STRPOS(t_notice.documentNumber, ...)` 使用**驼峰列名**。PG 未加引号折叠为小写 → `documentnumber` 不存在。 | 上游同一文件第 146 行是 `t_notice.document_number`，第 203 行误写为 `documentNumber`；**MySQL 下同样报 Unknown column**（MySQL 大小写不敏感但下划线敏感），属上游继承缺陷，被 W0 机械转换原样保留。仅当 `keywords` 非空时触发。 | 改为 `t_notice.document_number`，与同文件其余 3 处一致。 |
| N2 | `sa-base/.../heartbeat/HeartBeatRecordMapper.xml` `pageQuery` | `STRPOS(process_no, #{keywords})`，而 `process_no` 是 **INTEGER**。PG 无 `strpos(integer, text)`。 | 实测：`ERROR: function strpos(integer, unknown) does not exist`。上游用 `INSTR(process_no, ...)`，MySQL 会隐式转字符串，转换时只换了函数名没补 CAST。监控→心跳页按关键字搜索可达。 | 改为 `STRPOS(CAST(process_no AS TEXT), #{...}) > 0`。实测修复后返回 4 行。 |
| N3 | `sa-base/.../support/operatelog/OperateLogMapper.xml` `deleteByIds` | `delete from t_operate_log where id in (...)`，而该表主键是 `operate_log_id`。 | 上游同一处也是 `id`（上游缺陷）。当前无调用方，但该 XML **覆盖了 MyBatis-Plus 自动注入的同名 `deleteByIds`**（启动日志：`DeleteByIds Has been loaded by XML ... ignoring this injection`），留下一个「调用即 500」的地雷。 | 改为 `where operate_log_id in (...)`。 |
| N4 | `sa-base/.../support/datatracer/DataTracerMapper.xml` `selectRecord` | `where data_type = ?`，而该表列名是 `type`。 | 上游同一处也是 `data_type`（上游缺陷）；同文件 `query` 用的是正确的 `type`。当前无调用方。 | 改为 `where type = #{dataType}`。 |
| N5 | `sa-admin/.../oa/notice/NoticeMapper.xml` `queryEmployeeNotice` | `<if test="query.notViewFlag"> AND viewFlag = 0 </if>` 在 WHERE 里引用 SELECT 列别名。 | 上游同样写法；**MySQL 也禁止 WHERE 引用列别名**，所以这一分支在两端都是非法 SQL。当前不可达：`NoticeEmployeeService.queryList` 在 `notViewFlag=true` 时走的是 `queryEmployeeNotViewNotice`。 | 展开为等价的关联子查询 `(select count(*) from t_notice_view_record where employee_id=#{requestEmployeeId} and notice_id=t_notice.notice_id) = 0`，语义不变，同时消除潜在 500。 |
| N6 | `sa-base/.../codegenerator/.../MapperVariableService.java` | 代码生成器为「模糊查询」生成的 mapper XML 片段写死 `INSTR(...)`。 | 直接命中扫描词。PG 无 `INSTR`，生成出来的 mapper 无法执行。 | 改为 `STRPOS(a,b) > 0`（含单列、多列 OR 分组、Dict 三个分支）。 |
| N7 | `sa-base/.../code-generator-template/java/sql/Menu.sql.vm` | 生成的菜单 SQL 使用 MySQL 会话变量 `SET @parent_id = NULL; SELECT ... INTO @parent_id ...`。 | PG 无该语法。 | 改为相关子查询 `(SELECT menu_id FROM t_menu WHERE menu_name = '...' ORDER BY menu_id LIMIT 1)`，整段脚本可直接在 PG 粘贴执行。 |
| **N8** | `sa-base/.../support/operatelog/OperateLogMapper.xml` `queryByPage` | `CAST(create_time AS DATE) >= #{query.startDate}`，而 `OperateLogQueryForm.startDate` 是 **`String`**。PG 报 `operator does not exist: date >= character varying`。 | **真实运行证据**：探针 `POST /support/operateLog/page/query` 返回 `BadSqlGrammarException`。MySQL 会把字符串隐式转日期，PG 不会。 | 右侧补 CAST：`CAST(#{query.startDate} AS DATE)`，与 `HelpDocDao` / `MessageMapper` 的既有正确写法一致。修复后探针返回 `code=0`。 |
| **N9** | `sa-base/.../support/loginlog/LoginLogMapper.xml` `queryByPage` | 同 N8，`LoginLogQueryForm.startDate/endDate` 也是 **`String`**。 | 探针 `POST /support/loginLog/page/query` 返回同样的 `date >= character varying`。 | 同 N8。修复后探针返回 `code=0`。 |

> N6 / N7 属「代码生成器」这条活跃运行时路径：它不产生数据，但会把 MySQL 语法写进产物，是 MySQL 痕迹的再生源头，故一并归入 NEED_FIX。
>
> **N8 / N9 是本次收口最重要的增量发现**：静态 `PREPARE` 门禁对它们**完全无感**（未定型参数会被推断成 `date` 从而通过），只有真实运行、参数带真实 JDBC 类型时才会暴露。详见 §4.2。**同类风险面已全量排查**：全仓 21 处 `CAST(... AS DATE)` 比较中，其余 19 处的表单字段均为 `LocalDate`，安全；并新增 `tools/verify_mapper_param_types.py` 作为常驻门禁。

### 3.4 未修项与失败基线棘轮

`SmartAdminMapperPgValidationIT` 内置 `KNOWN_FAILURES` 显式基线，**已知失败集合不得扩大**（新增失败即测试变红）。当前 **1 条**：

| 语句 | 分类 | 原因 |
|---|---|---|
| `SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` | `DEAD_CODE` | `select serial_number_record_id from t_serial_number_record`，而该表**上游与本项目都没有这一列**（上游该表甚至无主键，只有 `uk_generator` 索引）。无任何调用方。修它需要先决定「返回什么」——属于发明未验证的行为，故只登记不改。 |

**收口前基线为 4 条，其中 3 条已随 V12 转绿并移出基线**：

| 语句（已移出） | 原分类 | 结局 |
|---|---|---|
| `EnterpriseEmployeeDao.queryPageEmployeeList` | `BLOCKED_ON_SCHEMA_DECISION` | V12 后 `PREPARE` 通过 → 移出基线 |
| `EnterpriseEmployeeDao.selectByEnterpriseIdList` | `BLOCKED_ON_SCHEMA_DECISION` | 同上 |
| `EnterpriseEmployeeDao.selectByEmployeeIdList` | `BLOCKED_ON_SCHEMA_DECISION` | 同上 |

即 `validated` 由 1311 升到 **1314**（+3 条原先被判失败的语句现在被 PG 成功解析），
`knownFailure` 由 4 降到 **1**，`failed` 仍为 **0**。棘轮因此**收紧**（要求恰好 1 条），
任何回退都会让测试变红。

---

## 4 D3：真实 PostgreSQL 测试

分两层：**静态层**用数据库自己解析全部 mapper SQL；**动态层**把真实端点打穿到真实库。
两层互补——**动态层抓到了静态层原理上抓不到的一类缺陷**。

### 4.1 静态层：mapper SQL 的 PG 解析门禁

新增 `sa-admin/src/test/java/net/lab1024/sa/admin/module/system/SmartAdminMapperPgValidationIT.java`。

**做法**：不执行、不改数据。对 `SqlSessionFactory` 注册的每条 MappedStatement：

1. 用 MyBatis 自己的 `ParamNameResolver` 构造「所有字段非空」的参数对象（所有 `<if>` 分支都会渲染，等于最大覆盖面）；
2. 取 `BoundSql` 得到带 `?` 占位符的最终 SQL，转成 `$n`；
3. 交给**真实 PostgreSQL** 执行 `PREPARE` —— 走完整 parse + 语义分析，表名/列名/函数名/类型不合法立即报错，**但不执行、不写数据**。

**结果**：

```
[mapper-pg-validation] validated=1314 failed=0 knownFailure=1 skipped=247
```

- `validated=1314`：1314 条语句的最终 SQL 被 PG 完整解析通过（收口前为 1311；+3 来自 V12 后 OA 三条语句转绿）。
- `failed=0`：无任何未登记失败。
- `skipped=247`：MyBatis-Plus 自动注入语句（`insert` / `updateById` / `update` / `insertBatch` …）因重载绑定名（`et` / `entityList`）无法用统一构造器生成参数而跳过。**这些语句的运行时行为由 W1/W2/W3 的 PG IT 覆盖**（`ProductPgIT`、`CustomerServiceIT`、`SupplierServiceIT`、`PricingIT` 等在真实 PG 上跑通了 insert/update/乐观锁），因此不是覆盖缺口。

**这条测试同时是「active SQL 是否仍有 MySQL 残留」的可执行门禁**：它不依赖人工审查 SQL 文本，而是让数据库自己判断。

### 4.2 动态层：真实端点功能探针（关键补充）

工具：`tools/smartadmin_feature_probe.mjs`（+ 账号夹具 `tools/smartadmin_probe_account.py`）。
路径**取自运行中后端的 `/v3/api-docs`（228 条）**，不是人工猜的。

**它为什么必要——静态层有一个原理性盲区。**

`PREPARE` 里的占位符是**未定型**的，PostgreSQL 会按上下文推断类型。对：

```sql
AND CAST(create_time AS DATE) >= $1
```

PG 会把 `$1` 推断成 `date`，于是 `PREPARE` **通过**。但 JDBC 真实执行时，
`OperateLogQueryForm.startDate` 是 `String`，驱动以 `varchar` 绑定，于是运行时炸：

```text
ERROR: operator does not exist: date >= character varying
```

这类缺陷的成因与 §9 的 D-1 完全同源——**MySQL 的隐式类型转换在 PostgreSQL 下不存在**。
静态解析看的是「SQL 本身是否合法」，而这类问题是「**SQL + 真实参数类型**才非法」，
只有把参数带真实类型跑一次才能暴露。

**结果**：探针首轮即命中 N8、N9（见 §3.3）。N8/N9 修复后复跑 53 项 → **52 通过 / 1 失败**，
唯一失败项是 §9 的 D-1（当时需用户决策，未自行修改）。
**D-1 经用户批准方案 A 并以 V12 修复后，最终复跑 → `total=53 ok=53 failed=0 unmappedPath=0`。**

### 4.3 第三层：缺陷类的常驻静态门禁

为避免 N8/N9 这类问题再次混入，新增 `tools/verify_mapper_param_types.py`：

1. 在每个 mapper 语句体内匹配 `CAST(<列> AS DATE) <比较符> #{a.b}`，且右侧**未**被 CAST 包裹；
2. 由 XML `namespace` 定位 Java Mapper 接口，按语句 `id` 定位方法，
   再用 `@Param("a")` 匹配出**表单参数的真实类型**（表单常是第二个参数，不能盲取第一个）；
3. 沿 `extends` 链解析字段 `b` 的声明类型；非日期类型即判缺陷。

设计上刻意避免两个陷阱（都在本次实测中踩到过）：

- **不按字段名全局查表**：`OperateLogQueryForm.startDate` 是 `String`，
  而 `FeedbackQueryForm.startDate` 是 `LocalDate`，同名不同类，全局查表会误报；
- **解析失败必须显式报警**：解析器失效会伪装成「0 缺陷」，
  因此脚本把「未解析」单独计数并打警告，绝不静默放行。

**验证**（正负样本）：把修复前的两行喂进去 → 正确判 `OperateLogQueryForm.startDate -> String => DEFECT`、
`LoginLogQueryForm.endDate -> String => DEFECT`；把修复后的 `CAST(#{...} AS DATE)` 喂进去 → 不再命中
（因为右侧已被 CAST，正则不再匹配——这正是修复生效的表现）。
当前全仓 **21 处命中全部解析为 `LocalDate`：0 缺陷，0 未解析**。
（原始扫描共 21 处，其中 2 处是已修的 N8/N9，故 §3.3 记作「其余 19 处为 `LocalDate`」。）

---

## 5 D4：MySQL 运行时痕迹归零

| 检索项 | 结果 | 判定 |
|---|---|---|
| `jdbc:mysql` / `jdbc:p6spy:mysql` | **0** | 4 套环境（dev/pre/prod/test）全部为 `jdbc:p6spy:postgresql://…:15432/xsy_scm?currentSchema=xsy_v2`（prod 为 `jdbc:postgresql:` + `org.postgresql.Driver`）。 |
| `mysql-connector` | 依赖 **0**；父 `pom.xml` 残留一个 `<mysql-connector-j.version>9.3.0</mysql-connector-j.version>` 属性 | `DEAD`：无任何 `dependency` 引用 `${mysql-connector-j.version}`，不进入运行时 classpath。属可清理的陈旧属性，不影响运行时。 |
| `DbType.MYSQL` | 2 处 | `COMMENT_ONLY`：`DataSourceConfig.java:105`、`MybatisPlusConfig.java:27` 的说明性注释。实际代码是 `DbType.POSTGRE_SQL`（Druid `setDbType` + `PaginationInnerInterceptor`）。 |
| `MySql` / `Mysql` | 19 处 | 全部为注释，或 `VelocityEngine`、`CodeGeneratorMapper.xml` 的 `'auto_increment'` 字面量等非 SQL 标识。 |
| `3306` | 全部为行政区划码（`330602` 等）与 1 份历史审计文档 | 非运行时配置。 |

**Active runtime 归零。**

补充：`CodeGeneratorMapper.xml` 的元数据查询已完整 PG 化（`current_schema()`、`obj_description()` / `col_description()` + `to_regclass()`、`information_schema.table_constraints` 取主键、`is_identity` / `nextval(%` 判自增），不再依赖 MySQL 的 `table_comment` / `column_comment` / `database()`。

---

## 6 D5：44 表反查模块 + SmartAdmin 原生功能验证

### 6.1 用 44 表清单反查当前正式源码

对 44 张表逐张检索 `@TableName` 实体与 mapper XML 引用：

- **42/44 有活跃绑定**（实体类 + mapper XML 或 MyBatis-Plus BaseMapper）。
- **2/44 零引用**：`t_category`、`t_goods` —— 与 W0 判定一致，维持不迁。

模块归属（摘要）：

| 模块 | 表 |
|---|---|
| 系统 · 登录/权限 | `t_employee`、`t_department`、`t_position`、`t_role`、`t_role_employee`、`t_role_menu`、`t_role_data_scope`、`t_menu`、`t_login_log`、`t_login_fail`、`t_password_log` |
| 系统 · 支撑 | `t_config`、`t_dict`、`t_dict_data`、`t_file`、`t_operate_log`、`t_data_tracer`、`t_change_log`、`t_code_generator_config`、`t_table_column`、`t_serial_number`、`t_serial_number_record`、`t_smart_job`、`t_smart_job_log`、`t_reload_item`、`t_reload_result`、`t_heart_beat_record`、`t_feedback` |
| 消息 / 文档 | `t_message`、`t_mail_template`、`t_notice`、`t_notice_type`、`t_notice_view_record`、`t_notice_visible_range`、`t_help_doc`、`t_help_doc_catalog`、`t_help_doc_relation`、`t_help_doc_view_record` |
| OA | `t_oa_bank`、`t_oa_enterprise`、`t_oa_enterprise_employee`、`t_oa_invoice` |
| 不迁 | `t_category`、`t_goods` |

### 6.2 SmartAdmin 原生功能真实连通性

方式：`tools/smartadmin_feature_probe.mjs`。造一个 `administrator_flag=TRUE` 的临时员工（管理员自动获得全部菜单权限），按 `EmployeeService.generateSaltPassword` 加盐规则 + Argon2 v5_8 参数生成口令，SM4 加密后走真实 `POST /login`（非生产环境直接返回图形验证码文本），再逐项打真实端点，最后登出并清理临时账号。

覆盖与结果见下表。判定口径：`code = 0` 为通过。

> **重要口径**：SmartAdmin 把**未匹配的路径交给静态资源处理器**，返回 **HTTP 200 + `code=10001`
> （NoResourceFoundException）**。因此**只看 HTTP 状态码会把「路径不存在」误判为成功**，
> 探针必须按 `code` 判定，并把「路径未映射」单独归类。首轮探针正是靠这一点发现
> 自己有一批路径写错（`/dict/*` 实际是 `/support/dict/*`），随后改用运行中后端的
> `/v3/api-docs`（228 条）取真实路径。

| 功能 | 端点 | 结果 |
|---|---|---|
| login / logout | `POST /login`、`GET /login/getLoginInfo`、`GET /login/getCaptcha`、`GET /login/logout` | **OK**（4/4） |
| employee | `/employee/query`（2 行）、`/employee/queryAll`、`/employee/getAllEmployeeByDepartmentId/{id}`、`/employee/getPasswordComplexityEnabled` | **OK**（4/4） |
| department | `/department/treeList`、`/department/listAll` | **OK**（2/2） |
| position | `/position/queryPage`、`/position/queryList` | **OK**（2/2） |
| role | `/role/getAll` | **OK** |
| menu | `/menu/query`（176 行）、`/menu/tree?onlyMenu=true`（11 行）、`/menu/auth/url`（232 行） | **OK**（3/3） |
| data scope | `/dataScope/list` | **OK** |
| dict | `/support/dict/getAllDict`、`/support/dict/getAllDictData`（4 行）、`/support/dict/queryPage` | **OK**（3/3） |
| config | `/support/config/query`、`/support/config/queryByKey?configKey=super_password` | **OK**（2/2） |
| file | `/support/file/queryPage`（6 行） | **OK** |
| operate log | `/support/operateLog/page/query`、`/support/operateLog/page/query/login` | **OK**（2/2）**← N8 修复后转绿** |
| login log | `/support/loginLog/page/query` | **OK** **← N9 修复后转绿** |
| notice / message | `/oa/notice/query`、`/oa/notice/employee/query`、**`/oa/notice/employee/query`（`notViewFlag=true`）**、`/oa/notice/employee/queryViewRecord`、`/oa/noticeType/getAll`（2 行）、`/support/message/queryMyMessage`、`/support/message/getUnreadCount` | **OK**（7/7）**← 覆盖 N1（`document_number`）与 N5（`notViewFlag` 子查询）两条修复路径** |
| job | `/support/job/query`（2 行）、`/support/job/log/query` | **OK**（2/2） |
| reload | `/support/reload/query`、`/support/reload/result/{tag}` | **OK**（2/2） |
| help | `/support/helpDoc/query`、`/support/helpDoc/user/queryAllHelpDocList`、`/support/helpDoc/helpDocCatalog/getAll` | **OK**（3/3） |
| code generator | `/support/codeGenerator/table/queryTableList`（57 张表）、`/…/getTableColumns/t_notice`（18 列）、`/…/getConfig/t_notice` | **OK**（3/3） |
| 其他系统 | `/support/serialNumber/all`、`/support/heartBeat/query`（8 行）、`/support/changeLog/queryPage`、`/support/feedback/query`、`/support/dataTracer/query`、`/support/tableColumn/getColumns/1` | **OK**（6/6） |
| OA | `/oa/enterprise/page/query`、`/oa/enterprise/query/list`、`/oa/bank/page/query`、`/oa/invoice/page/query` | **OK**（4/4） |
| **OA 企业-员工** | `/oa/enterprise/employee/queryPage` | **OK** **← V12 修复后转绿**（`code=0`）。收口前为 `operator does not exist: character varying = bigint`，即 §9 的 **D-1**。 |

**汇总：`total=53  ok=53  failed=0  unmappedPath=0`。**

> 这是本次收口的最终数字（V12 修复后复跑）。中间态：N8/N9 修复后为 `ok=52 failed=1`（唯一失败项即 D-1）。

逐项结果与原始 `code`/`msg` 记录在 `.runtime/smartadmin-feature-probe.json`。

> `t_mail_template` 只有实体（MyBatis-Plus BaseMapper），无独立 mapper XML，也没有对外查询端点，故不在探针清单内。

**这份探针的实际价值不止于「证明能用」**：它是本次唯一发现 N8/N9 的手段（§4.2），
也顺带证实了 N1 与 N5 两条修复在真实运行下确实生效（`document_number` 关键字查询与
`notViewFlag=true` 分支都返回 `code=0`）。

---

## 7 D6：02–08 后续业务迁移参考资产登记

`project-reference-examples/xsy-scm/postgresql/02–08` 已登记为**后续业务迁移参考资产**。本阶段未迁入正式数据库，也未被复制为 Flyway migration。

| 文件 | 表数 | 对应后续工作 | 当前状态 |
|---|---|---|---|
| `02-商品与价格.sql` | 5（`t_product`、`t_product_category`、`t_product_price`、`t_product_sku`、`t_product_supplier`） | — | **不迁**：W1 已完成，V2 以 `product_spu` / `product_sku` / `product_category` / `product_image` 建模，**不允许被 C 旧表覆盖** |
| `03-客户.sql` | 4（`t_customer`、`t_customer_goods_visible`、`t_customer_period`、`t_customer_qrcode`） | — | **不迁**：W2/W3 已完成（`customer` / `customer_type` / `customer_sku_visibility`），**不允许被 C 旧表覆盖** |
| `04-订单.sql` | 4（`t_order`、`t_order_item`、`t_order_log`、`t_refund`） | **Order** | 后续参考 |
| `05-采购.sql` | 4（`t_purchase_order`、`t_purchase_item`、`t_receive`、`t_supplier`） | **Purchase** | 后续参考 |
| `06-库存.sql` | 5（`t_stock_balance`、`t_stock_flow`、`t_stock_adjust`、`t_stock_check`、`t_stock_check_item`） | **Inventory** | 后续参考 |
| `07-补充模块P1.sql` | 23（询价 / 单位换算 / 客户折扣 / 打印模板 / 数据大屏 / 凭证 / 溯源 / 外部对接 …） | **P1 补充模块** | 后续参考 |
| `08-财务.sql` | 2（`t_payment`、`t_receivable`） | **Finance** | 后续参考 |

**登记约束（后续工作必须遵守）**：
1. 这些文件**严格只读**，禁止直接执行到 `xsy_v2`，禁止复制为正式 Flyway migration。
2. 它们描述的是**线上 MySQL 结构**，其类型映射沿用 C 的 `tinyint → SMALLINT` 策略，**与 V2 已确认的 `tinyint(1) → BOOLEAN` 不同**；迁入时必须按 V2 规则重新判定，不得为对齐 C 而把 BOOLEAN 改回 SMALLINT。
3. C 的 `xsy_set_update_time()` + `BEFORE UPDATE` 触发器**不引入**：V2 已由 SmartAdmin / MyBatis-Plus 维护 `update_time`。除非能证明某张正式系统表完全没有应用层维护且已产生真实 bug。
4. 迁入时必须与 W1–W3 已完成的 Product / Customer / Pricing 建模对齐，**不得覆盖**。
5. `99-初始化数据.sql`、`all-in-one.sql` **仅作历史/参考**，禁止执行到正式 V2；V2 保持 clean SmartAdmin seed，不导入 C 的 demo/runtime 数据。

---

## 8 冻结与门禁证据

### 8.1 Migration 冻结

`V1–V11` **未做任何修改**（收口后复算哈希与下表逐字节一致）：

| 文件 | SHA-256 | 状态 |
|---|---|---|
| `V1__sa_system_poc_core.sql` | `64d385fd4f9b9a1a585259e814f2a97892b4d7e725250cecef880118a3119e21` | ✅ 未变 |
| `V2__sa_system_login_rbac.sql` | `4213859f12dee255eb81c66edca62c79521ee580fac3f2260e35f6da6e33d2f8` | ✅ 未变 |
| `V3__sa_system_login_support_and_seed.sql` | `cc87ab056d5b42581bcd929919004c82cd400c074ccb6efac8f1fdf74c209a3d` | ✅ 未变 |
| `V4__sa_system_runtime_support.sql` | `9fa17cdb23a4b96ef570609234d308e51ec14cbb52dbe85feaf4f9bbd761946c` | ✅ 未变 |
| `V5__sa_system_remaining_tables.sql` | `710a663b6aac121ec03b05ee763f74a4dda4a3c9ebfac9ac0f4a6ac549799ecd` | ✅ 未变 |
| `V6__scm_product.sql` | `b97089324ea808de5fb98fea9b39c691dd77c6064aa6eae823788bb8ea709e8d` | ✅ 与 W1/W2/v3 基线一致 |
| `V7__scm_product_permissions.sql` | `f85d0042d537067ebec48e66e3d456fc5b13d632f0ef1d280ee2357a8da6b9d7` | ✅ 同上 |
| `V8__scm_customer_supplier.sql` | `0a83720ed4b93f780b7a230b68d3778e55301eb816fc3566099a14042ccf6ed5` | ✅ 与 W2/v3 基线一致 |
| `V9__scm_customer_supplier_permissions.sql` | `0cf02ca7e63734f8936d5d8668d7e6dade87133c39ece310f080cbd22638c48b` | ✅ 同上 |
| `V10__scm_pricing.sql` | `cb314708bff5ecbcd3b7eb9f907acbfba8883eb4456f2b0d39669e7a800f6c30` | ✅ 与 v3 基线一致 |
| `V11__scm_pricing_permissions.sql` | `3b74f754236841404f1c5510bd12a895a09f116514e205becb4ecf92865252e5` | ✅ 同上 |

**新增（本阶段唯一 migration）**：

| 文件 | SHA-256 | 说明 |
|---|---|---|
| `V12__sa_oa_enterprise_employee_bigint.sql` | `97ad075b801a75984ad9a4e1ab494a5df72b88838f0b3594155f437c15415e4a` | D-1 方案 A：两个关联列 `VARCHAR(100) → BIGINT`。**首次成功应用后**计算并冻结。 |

**正式 hash manifest**：`docs/architecture/pg-closure-applied-migrations.sha256`（**V1–V12 全集**）。
`sha256sum -c` 12/12 `OK`。

> **manifest 归属说明**：既有三个 manifest 是**逐波次**登记的
> （`w1-…`=V6/V7、`w2-…`=V6–V9、`v3-…`=V6–V11）。本阶段新增的是系统层 schema 修复，
> 不属于 W1/W2/W3 任何一波，因此**新建本阶段的 manifest** 而不是把 V12 塞进 `v3-…`
> （那会让「v3 = W3 波次」的语义失真）。顺带把 `V1–V5` 的基线哈希正式登记下来——
> 收口报告 §2.3 曾指出它们「此前未单独登记」。`v3-applied-migrations.sha256` **保持原样未改**。

Flyway `xsy_v2.flyway_schema_history`：**12 条记录全部 `success = t`**，校验和与上表对应。
`migrate` / `validate` 均 PASS（证据：`SmartAdminOaEnterpriseEmployeeMigrationIT`，`Tests run: 1, Failures: 0, Errors: 0`）。

**V12 落库后实测**（`xsy_v2.t_oa_enterprise_employee`）：

```text
enterprise_employee_id  bigint / int8
enterprise_id           bigint / int8      ← 原 character varying(100)
employee_id             bigint / int8      ← 原 character varying(100)
update_time             timestamp
create_time             timestamp
```

唯一键 `uk_t_oa_enterprise_employee_uk_enterprise_employee(enterprise_id, employee_id)` 与
`idx_…_idx_enterprise_id` / `idx_…_idx_employee_id` 由 PostgreSQL **自动随列类型重建**，均保留。
该表在 V5 中**未播种任何数据**，`USING …::BIGINT` 转换零数据风险。

### 8.2 门禁（收口后最终复跑）

| 门禁 | 命令 | 结果 | 证据 |
|---|---|---|---|
| Flyway migrate / validate | `SmartAdminOaEnterpriseEmployeeMigrationIT` | **PASS**（12 条 history 全 `success=t`） | `.runtime/d1-v12-migration-it.log` |
| 结构 diff | `python tools/verify_system_schema_diff.py` | SAME 32 / INTENTIONAL 12 / **DEFECT 0** | `.runtime/g10-schema-diff.log` |
| mapper PG 解析 | `mvn -Ptest -pl sa-admin -am test -Dtest=SmartAdminMapperPgValidationIT -Dsurefire.failIfNoSpecifiedTests=false` | **validated=1314 / failed=0 / known=1** | `.runtime/g2-backend-it.log` |
| 日期参数类型 | `python tools/verify_mapper_param_types.py` | 命中 21 / **DEFECT 0** / 未解析 0 | §4.3 |
| **功能探针** | `node tools/smartadmin_feature_probe.mjs` | **total 53 / ok 53 / failed 0 / unmappedPath 0** | `.runtime/smartadmin-feature-probe.json` |
| 后端全量单测 | `mvn -Ptest test` | 120 / 0 / 0 / 0 | `.runtime/g1-backend-unit.log` |
| 后端 PG 集成测试 | `mvn -Ptest test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false` | **96** / 0 / 0 / 0 | `.runtime/g2-backend-it.log` |
| 前端单测 | `npm test` | 20 / 0 | `.runtime/…`（`node --test`） |
| 前端 ESLint | `npm run lint`（`eslint src`） | **0 error** / 3 warning（全为上游既有） | `.runtime/g7-…` |
| 前端构建 | `npm run build`（`--outDir dist-verify`） | BUILD SUCCESS（`✓ built in 47.09s`） | `.runtime/g7-web-build.log` |
| TS baseline 棘轮 | `python tools/ts_baseline_ratchet.py check` | **PASS**（new 0 / SCM 0 / total 1973 ≤ 1974） | `.runtime/g5-ts-ratchet.log` |
| W1/W2/W3 Playwright | `npx playwright test` | **8 passed** | `.runtime/g9-playwright.log` |
| V1–V12 冻结 | `sha256sum -c docs/architecture/pg-closure-applied-migrations.sha256` | **12/12 OK** | §8.1 |
| reference 只读 | `git status --porcelain -- project-reference-examples/` | **空**（零修改） | §1 |

### 8.3 回归数字

| 回归 | 命令 | Tests run | Failures | Errors | Skipped | 结果 |
|---|---|---|---|---|---|---|
| 后端全量单测 | `mvn -Ptest test` | 120 | 0 | 0 | 0 | BUILD SUCCESS |
| 后端 PG 集成测试 | `mvn -Ptest test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false` | 96 | 0 | 0 | 0 | BUILD SUCCESS |
| 前端单测 | `npm test` | 20 | 0 | 0 | 0 | pass 20 / fail 0 |
| W1/W2/W3 Playwright | `npx playwright test` | 8 | 0 | 0 | 0 | 8 passed |

对照收口前基线（单测 120、PG IT 95）：PG IT **+1**，即本次新增的
`SmartAdminOaEnterpriseEmployeeMigrationIT`（`Tests run: 1`）。其余全部持平，
说明 V12 与 KNOWN_FAILURES 收紧**未引入任何回归**。

W1/W2/W3 的 Playwright 8 例逐项：

```text
ok 1 scm-customer.spec.ts  live customer pilot: type, credit period, status, search, deep link and deletion
ok 2 scm-customer.spec.ts  read-only role cannot mutate customers and buttons are hidden
ok 3 scm-pricing.spec.ts   pricing resolver keeps price and sale eligibility independent
ok 4 scm-pricing.spec.ts   read-only role cannot mutate pricing and successful batch keys are protected
ok 5 scm-product.spec.ts   live product pilot: categories, SKU delta, SPU images, search, deep link and deletion
ok 6 scm-product.spec.ts   read-only role cannot mutate products and buttons are hidden
ok 7 scm-supplier.spec.ts  live supplier pilot: SKU relations with R12 defaults, whole-table clear, status and deletion
ok 8 scm-supplier.spec.ts  read-only role cannot mutate suppliers and buttons are hidden
```

**测试库口径说明**：`test` profile 的 `spring.datasource.url` 默认值即
`jdbc:p6spy:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2`（见 `sa-base/src/main/resources/test/sa-base.yaml`），
即 PG IT 跑在 **`xsy_scm`** 上，与 W1–W3 基线一致。本次一度误用 `XSY_V2_DB_URL` 覆盖成
`xsy_scm_test` 并丢掉了 `p6spy:` 前缀，导致 Druid 连接失败；已回退为不覆盖该环境变量后重跑通过。
`xsy_scm_test` 库中并无 `xsy_v2` schema（无 Flyway 历史），**不可作为本阶段 PG IT 的目标库**。

---

## 9 D-1：`t_oa_enterprise_employee` 关联列类型不匹配（**已按方案 A 修复并闭环**）

> **最终裁决**：用户批准**方案 A**。已新增 `V12__sa_oa_enterprise_employee_bigint.sql` 完成
> schema 根因修复，`migrate`/`validate` PASS，`POST /oa/enterprise/employee/queryPage` 返回 `code=0`，
> 3 条 `BLOCKED_ON_SCHEMA_DECISION` 移出 `KNOWN_FAILURES`。**本节以下保留决策当时的原始记录，
> 末尾追加闭环证据。**
>
> **未采用方案 B**（mapper `CAST` workaround）：`WHERE`/`JOIN` 上的表达式会让 `idx_enterprise_id`
> 与 `uk_enterprise_employee` 失效，且遇到非数字值会直接报错，属治标。

### D-1（原始记录）`t_oa_enterprise_employee` 关联列类型不匹配 → OA 企业-员工功能在 PostgreSQL 下不可用

**事实**（两侧均已核对）：

| 对象 | 上游 MySQL `smart_admin_v3.sql` | 当前 V2 `V5` | Java 实体 |
|---|---|---|---|
| `t_oa_enterprise_employee.enterprise_id` | `varchar(100)` | `character varying(100)` | `Long enterpriseId` |
| `t_oa_enterprise_employee.employee_id` | `varchar(100)` | `character varying(100)` | `Long employeeId` |
| `t_oa_enterprise.enterprise_id` | `bigint` | `bigint` | `Long` |
| `t_employee.employee_id` | `bigint` | `bigint` | `Long` |

**为什么 MySQL 上没暴露**：MySQL 在 `varchar` 与 `bigint` 比较/连接时会隐式把两边转成数值，所以 `where enterprise_id = 1` 和 `join … on e.enterprise_id = en.enterprise_id` 都能跑通。PostgreSQL 对比较运算符**没有隐式数值转换**。

**实测（真实 PostgreSQL）**：

```
# 连接
select count(*) from t_oa_enterprise_employee e
  left join t_oa_enterprise en on e.enterprise_id = en.enterprise_id;
→ ERROR: operator does not exist: character varying = bigint

# JDBC 强类型参数（Long → int8）比较
PREPARE zz AS select * from t_oa_enterprise_employee where enterprise_id = $1::bigint;
→ ERROR: operator does not exist: character varying = bigint
（employee_id = $1::bigint / employee_id in ($1::bigint) / delete … where enterprise_id = $1::bigint 同样报错）

# 但 INSERT 可以（PG 自 8.3 起允许数值 → text 的赋值转换）
PREPARE p1 AS INSERT INTO t_oa_enterprise_employee(enterprise_id,employee_id) VALUES ($1::bigint,$2::bigint);
→ PREPARE 成功
```

**影响面**：`EnterpriseEmployeeDao` 的**查询与删除**路径（`selectByEnterpriseId`、`selectByEnterpriseAndEmployeeIdList`、`selectByEmployeeIdList`、`selectByEnterpriseIdList`、`queryPageEmployeeList`、`selectEnterpriseIdByEmployeeId`、`selectEmployeeIdByEnterpriseIdList`、`deleteByEnterpriseAndEmployeeIdList`、`deleteByEmployeeId`）在运行时全部失败 → **OA「企业关联员工」页面不可用**。写入（新增关联）本身可用，但写入后读不回来。

**为什么当时不修**：正确修法是把这两列改为 `BIGINT`（V2 侧实体就是 `Long`），这属于**重构已有 V1–V5 表结构**。按当时审计规则「若发现需要重构已有 V1–V5 表结构或有破坏性修改，停止并报告，不自行修改」，此处只报告，不改。

**可选方案**（**用户已裁定：方案 A**）：

| 方案 | 动作 | 评价 | 裁决 |
|---|---|---|---|
| **A（推荐）** | 新增 `V12`：`ALTER TABLE t_oa_enterprise_employee ALTER COLUMN enterprise_id TYPE BIGINT USING enterprise_id::bigint;`（`employee_id` 同） | 一次修好，语义与实体一致，后续 OA 可用；`uk_enterprise_employee(enterprise_id, employee_id)` 与 `idx_*` 会自动随列类型重建。当前该表无数据（V5 未播种），`USING` 转换零风险。**但这会修改 V5 已建表 → 需用户批准。** | ✅ **已批准并实施** |
| B | 只在 mapper 里加显式 CAST（`e.enterprise_id::bigint = en.enterprise_id`、`= #{id}::bigint`） | 不动 DDL，但 `WHERE`/`JOIN` 上的表达式会使 `idx_enterprise_id` / `uk_enterprise_employee` 失效；且若将来出现非数字值会直接报错。属治标。 | ❌ 未采用 |
| C | 不修，OA 企业-员工模块标记为不可用 | 若 OA 仅为 SmartAdmin 示例模块、V2 不打算用，可接受。需要用户确认 OA 是否在保留范围内。 | ❌ 未采用 |

**运行时确认**（`tools/smartadmin_feature_probe.mjs`）：`POST /oa/enterprise/employee/queryPage`
实际返回 `BadSqlGrammarException` → `operator does not exist: character varying = bigint`，
失败 SQL 为：

```sql
SELECT COUNT(*) AS total FROM t_oa_enterprise_employee
  LEFT JOIN t_oa_enterprise ON t_oa_enterprise_employee.enterprise_id = t_oa_enterprise.enterprise_id
  LEFT JOIN t_employee       ON t_oa_enterprise_employee.employee_id = t_employee.employee_id
 WHERE t_oa_enterprise_employee.enterprise_id = ? AND (STRPOS(...) > 0 OR ...)
```

这是 53 项探针中**唯一**的失败项。

> 说明：本次 `SmartAdminMapperPgValidationIT` 的静态解析只抓到了 3 条（带 JOIN 的），而**运行时**受影响的更多——因为 `PREPARE` 看到的是「无类型参数」，PostgreSQL 可以把它推断成 `text` 从而解析通过；只有 JDBC 真正以 `int8` 绑定时才会失败。这一点已在本节说明，避免把「静态门禁通过」误读成「运行时没问题」。**N8/N9 是同一盲区的另一实例**（见 §4.2、§4.3），区别只是它们不需要改 DDL 就能修，所以本次直接修掉了。

### 9.1 闭环证据（方案 A / V12）

**变更面**（只加不改）：

| 类型 | 文件 |
|---|---|
| 新增 migration | `xsy-scm-server/sa-admin/src/main/resources/db/migration/V12__sa_oa_enterprise_employee_bigint.sql` |
| 新增测试 | `xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/system/SmartAdminOaEnterpriseEmployeeMigrationIT.java` |
| 收紧棘轮 | `SmartAdminMapperPgValidationIT.java`：`KNOWN_FAILURES` 4 → 1 |
| 登记裁决 | `tools/verify_system_schema_diff.py`：`t_oa_enterprise_employee` 加入 `ADJUDICATED_INTENTIONAL` |
| 冻结清单 | `docs/architecture/pg-closure-applied-migrations.sha256`（V1–V12） |

**未做**（刻意）：未在 mapper XML 里加任何 `CAST`（`EnterpriseEmployeeMapper.xml` 零修改）；
未改 `V1–V11`；未顺手加宽 `login_pwd`、未删 `mysql-connector-j.version` 属性、未动 dead code。

**四条独立证据**：

1. **schema**：`information_schema.columns` 两列 `data_type=bigint` / `udt_name=int8`，
   `character_maximum_length` 为 NULL；唯一键 `contype='u'` 保留且两个键列 `atttypid='int8'`。
2. **静态解析**：`validated=1314 failed=0 knownFailure=1`（3 条原失败语句转绿并移出基线）。
3. **动态运行**：`tools/smartadmin_feature_probe.mjs` → `total=53 ok=53 failed=0 unmappedPath=0`；
   `POST /oa/enterprise/employee/queryPage` → `http=200 code=0 msg=操作成功`。
4. **行为回归**：`SmartAdminOaEnterpriseEmployeeMigrationIT` 实际执行了原先失败的两类语句
   （JOIN 比较、JDBC `setLong` 强类型参数），并验证了写→读回环（探测行写入后读回 1 行、清理后为 0）。

**边界**：该表在 V5 中未播种数据，因此 `USING …::BIGINT` 的转换在**当前所有环境**都是空表操作；
即便将来有数据，`USING` 也是显式数值转换而非静默截断——非数字值会直接让 migration 失败，
不会产生脏数据。

---

## 10 风险与遗留（全部非阻塞）

1. **D-1 已闭环**（§9）。OA 企业-员工功能在 PostgreSQL 下已可用，53 项探针 **53/53**。**无剩余待决策项。**
2. **`SmartAdminMapperPgValidationIT` 的 247 条 skipped** 是 MyBatis-Plus 自动注入语句，靠 W1–W3 的 PG IT 覆盖运行时行为，不在本门禁的静态解析范围内。
3. **静态解析门禁的边界（本次已实证，且仍然存在）**：`PREPARE` 能抓「列名/函数名/表名错误」和「两个强类型列之间的运算不匹配」，但**抓不到「无类型参数 vs 强类型列」的运行时绑定失败**。
   - 本次三个实例：D-1（varchar 列 vs `Long` 参数，**已由 V12 从 schema 根因消除**）、N8/N9（`date` 列 vs `String` 参数，**已在 mapper 层修复**）。
   - **这不是理论风险，而是本次真实漏检**：N8/N9 在静态门禁下 1311 条全绿，是 §4.2 的动态探针才把它们挖出来的。
   - 补位手段已经落地两层：动态探针（§4.2）+ 缺陷类静态门禁 `tools/verify_mapper_param_types.py`（§4.3）。
   - **仍未覆盖的剩余面**：探针只打「查询型」端点，写路径的同类问题（如 `insert` 时把 `String` 写入日期列——PG 允许赋值转换，通常不报错）与未被探针触及的端点，需要后续波次在真实业务用例里覆盖。
4. **`t_employee.login_pwd VARCHAR(100)` 余量 3 字符**（§2.3），当前无 bug。**本次刻意未加宽**（避免扩大改动面）。
5. **父 `pom.xml` 的 `<mysql-connector-j.version>` 属性未被引用**，可清理（不影响运行时）。**本次刻意未动**。
6. **`SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` 是 dead code**：语句本身非法（MySQL 下同样非法），无任何调用方，故保留在 `KNOWN_FAILURES` 棘轮基线中。`t_serial_number_record` 上游遗留无主键——若将来需要该表的行标识，需要**一个新的 migration** 加代理主键（**不是 V12**，V12 已被 D-1 占用）。
7. **C 侧 `01-系统基础表.sql` 的 `login_pwd` 等字段与 v3.31 官方 DDL 存在偏差**（C 取自线上库），后续引用 C 资产时应以 v3.31 DDL / V2 实测为准，不要反向对齐 C。**`t_oa_enterprise_employee` 现在是这一原则的正式先例**——已登记进 `tools/verify_system_schema_diff.py` 的 `ADJUDICATED_INTENTIONAL`。
8. **探针为一次性运行，非常驻门禁**：`tools/smartadmin_feature_probe.mjs` 需要后端 dev(18080) + PostgreSQL 同时在线，本次以人工触发方式运行。若希望它成为常规回归的一部分，需要接入 W1–W3 的 Playwright/e2e 流程——**本阶段未做，避免扩大改动面**。

---

## 11 本阶段交付物与工具清单

**报告**：本文档。

**新增 migration（本阶段唯一 DDL，经用户批准）**：

| 文件 | 作用 |
|---|---|
| `xsy-scm-server/sa-admin/src/main/resources/db/migration/V12__sa_oa_enterprise_employee_bigint.sql` | D-1 方案 A：`t_oa_enterprise_employee.enterprise_id` / `employee_id` 由 `VARCHAR(100)` 改为 `BIGINT`（`USING …::BIGINT`） |

**新增测试/工具**：

| 文件 | 作用 |
|---|---|
| `xsy-scm-server/sa-admin/src/test/java/.../module/system/SmartAdminMapperPgValidationIT.java` | 静态门禁：**1314 条** mapper 语句交真实 PG `PREPARE`，内置 **1 条**失败棘轮 |
| `xsy-scm-server/sa-admin/src/test/java/.../module/system/SmartAdminOaEnterpriseEmployeeMigrationIT.java` | **V12 落地验证**：断言两列为 `bigint`/`int8`、唯一键与索引保留、原先失败的 JOIN 与强类型参数语句可执行 |
| `docs/architecture/pg-closure-applied-migrations.sha256` | 正式 hash manifest：**V1–V12 全集** |
| `tools/smartadmin_feature_probe.mjs` | 动态探针：真实登录 + 53 项端点打真实 PG |
| `tools/smartadmin_probe_account.py` | 探针账号夹具（argon2 加盐口令，与 `EmployeeService.generateSaltPassword` 一致） |
| `tools/verify_mapper_param_types.py` | 缺陷类静态门禁：日期列 vs 非日期参数 |
| `tools/verify_system_schema_diff.py` | D1 结构 diff（可重跑） |
| `xsy-scm-server/.runtime/mvnw.sh`、`run-backend.sh` | 本机 Maven / 后端启动脚本（`.runtime/` 已 gitignore） |

**修改的既有文件（11 处）**：

| 类别 | 文件 |
|---|---|
| mapper XML（N1、N5） | `NoticeMapper.xml` |
| mapper XML（N2） | `HeartBeatRecordMapper.xml` |
| mapper XML（N3、N8） | `OperateLogMapper.xml` |
| mapper XML（N4） | `DataTracerMapper.xml` |
| mapper XML（N9） | `LoginLogMapper.xml` |
| 代码生成器（N6） | `MapperVariableService.java` |
| 代码生成器模板（N7） | `Menu.sql.vm` |
| 棘轮基线收紧 | `SmartAdminMapperPgValidationIT.java`（`KNOWN_FAILURES` 4 → 1） |
| diff 裁决登记 | `tools/verify_system_schema_diff.py`（`t_oa_enterprise_employee` 加入 `ADJUDICATED_INTENTIONAL`） |

**明确未改**：`EnterpriseEmployeeMapper.xml`（**零修改**，未加任何 `CAST`）、`V1–V11`、
`project-reference-examples/xsy-scm/**`（零修改）、`t_employee.login_pwd`、父 `pom.xml`。

---

## 12 停止点

**SmartAdmin PostgreSQL Closure = GO。** 本报告完成后**停止，不进入 W4**，
不启动 PostgreSQL Full Compatibility 的后续批次，**不新增 V13+**。
`02–08` 保持参考资产状态，等待用户后续波次指令。

已交付的最终状态（与 §0 一致）：

```text
SmartAdmin PostgreSQL Closure = GO
active runtime MySQL dependency = 0
active Mapper MySQL dialect    = 0
system schema coverage         = 100%
feature probe                  = 53/53 PASS
W1/W2/W3 regression            = PASS
V1-V11 unchanged
V12 frozen
```
