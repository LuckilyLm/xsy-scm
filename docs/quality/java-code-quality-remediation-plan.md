# XSY-SCM Java 代码质量与工程规范整改计划

> 状态：执行中  
> 基线：`main @ a93a7772b2f56107778e834559d427176e3af0da`  
> 当前业务阶段：`F1-3B COMPLETE`  
> 当前暂停：`F1-3C / F1-4 / F1-5 / F1-6 / F1-8`
>
> 本质量整改完成前，**暂停新增业务功能**。
>
> 本阶段只允许：
>
> - `chore`
> - `refactor`
> - `test`
> - `docs`
>
> 不允许新增：
>
> - 业务表
> - 业务状态
> - 业务 API
> - 页面
> - Finance 新阶段功能

---

## 1. 整改目标

本轮不是“美化代码”。

目标是建立一套：

> AI 和开发人员即使忘记规范，CI 也能阻止低质量代码继续进入 `main`

的工程质量体系。

核心治理范围：

1. Java 包名
2. Java 命名
3. Magic String / Magic Value
4. Enum 与状态模型
5. Permission
6. 参数校验
7. 公共工具复用
8. 注释
9. Service 职责
10. 分层架构
11. import / 格式
12. 文档
13. 工程目录
14. 自动质量门禁

---

## 2. 规范来源

本项目 Java 代码规范参考：

1. Alibaba Java Coding Guidelines
2. Google Java Style
3. Spring Boot / Spring Framework 官方实践
4. SmartAdmin 现有工程结构
5. XSY-SCM 当前真实业务约束

其中：

- Alibaba Java Coding Guidelines 作为业务 Java 编码规则主要参考。
- Google Java Style 作为格式、import、源码组织补充参考。
- Spring 官方能力优先复用，不得重复实现已有基础工具。
- 项目业务事实仍以 `docs/requirements`、`docs/decisions`、真实代码、DB schema 为准。
- 外部规范不得反向改变业务规则。

---

# 3. Package Namespace 治理

## 3.1 当前问题

当前 SCM 包路径：

```text
net.lab1024.sa.admin.module.scm.*
```

存在问题：

- 包名过长
- import 冗长
- Java 文件有效信息密度降低
- AI 上下文被重复前缀大量消耗
- XSY 自有业务与 SmartAdmin 底座边界不直观

## 3.2 正式目标

XSY 自有 SCM 业务统一使用：

```text
com.xsy.scm
```

目录：

```text
com.xsy.scm
├── common
├── product
├── customer
├── supplier
├── pricing
├── order
├── purchase
├── inventory
├── sorting
├── delivery
├── finance
├── report
├── dashboard
└── screen
```

例如：

```text
com.xsy.scm.product.service.ProductCategoryService
com.xsy.scm.finance.service.FinancePaymentService
com.xsy.scm.inventory.dao.InventoryBalanceDao
```

禁止继续新增：

```text
net.lab1024.sa.admin.module.scm.*
```

## 3.3 SmartAdmin 底座

本轮不迁移：

```text
net.lab1024.sa.base.*
net.lab1024.sa.admin.module.system.*
net.lab1024.sa.admin.module.support.*
```

原因：

这些仍属于 SmartAdmin 底座。

不要为了缩短 import，把底座和业务代码混在同一次迁移。

## 3.4 Spring 扫描

当前：

```java
AdminApplication.COMPONENT_SCAN = "net.lab1024.sa";
```

迁移后必须同时扫描：

```text
net.lab1024.sa
com.xsy
```

`ComponentScan` 与 `MapperScan` 必须同时验证。

目标语义：

```java
@ComponentScan({
    SMART_ADMIN_PACKAGE,
    XSY_PACKAGE
})

@MapperScan(
    value = {
        SMART_ADMIN_PACKAGE,
        XSY_PACKAGE
    },
    annotationClass = Mapper.class
)
```

不得漏掉：

- Service
- Controller
- Component
- Mapper

## 3.5 迁移范围

必须同时更新：

```text
src/main/java
src/test/java

Java package declaration
Java import

MyBatis Mapper XML namespace
MyBatis XML resultType / parameterType 全限定名
Spring ComponentScan
MapperScan
ArchUnit package rules
反射字符串
测试扫描路径
脚本中的 package prefix
文档中的正式代码路径
```

禁止简单全仓字符串替换后直接提交。

必须使用 IDE / AST / 可验证的安全重构方式。

## 3.6 Package 迁移验收

迁移完成后：

```text
src/main/java 下：

net.lab1024.sa.admin.module.scm
= 0

src/test/java 下：

net.lab1024.sa.admin.module.scm
= 0
```

允许文档 / Git 历史中出现旧包名。

---

# 4. Naming / 命名规范

## 4.1 基础命名

Java 类型统一采用 `UpperCamelCase`：

```java
ProductCategoryService
ProductSpuDao
FinancePaymentService
ScmOrderRefundStatusEnum
```

变量、字段、参数、方法统一采用 `lowerCamelCase`：

```java
productCategoryService
productSpuDao
financePaymentService
refundStatus
customerId
```

常量采用：

```text
UPPER_SNAKE_CASE
```

例如：

```java
MAX_BATCH_SIZE
PAYMENT_ADD_SCOPE
DEFAULT_PAGE_SIZE
```

## 4.2 名称必须表达业务语义

仅符合驼峰格式还不够。

变量名称必须能够在脱离类型声明后仍大致表达其职责。

禁止成员变量使用过度泛化名称：

```java
private final ProductCategoryDao dao;
private final ProductSpuService service;
private final ProductQueryService query;
private final ProductAggregateValidator validator;
private final ProductSkuSyncManager manager;
```

应改为：

```java
private final ProductCategoryDao productCategoryDao;
private final ProductSpuService productSpuService;
private final ProductQueryService productQueryService;
private final ProductAggregateValidator productAggregateValidator;
private final ProductSkuSyncManager productSkuSyncManager;
```

如果同一个类中存在多个相同角色依赖，该规则尤其必须遵守。

## 4.3 DAO 命名

DAO 字段必须优先采用：

```text
<Entity/Domain>Dao
```

对应变量：

```text
<Entity/Domain lowerCamel>Dao
```

例如：

```java
private final ProductCategoryDao productCategoryDao;
private final ProductSpuDao productSpuDao;
private final FinancePaymentDao financePaymentDao;
private final SalesOrderDao salesOrderDao;
```

禁止：

```java
dao
spuDao
paymentDao     // 若上下文存在歧义
d
```

其中：

```java
productSpuDao
```

优于：

```java
spuDao
```

因为完整名称在：

- 阅读方法主体
- 搜索代码
- AI 上下文
- Code Review
- 后续增加第二个 DAO

时都有更明确的语义。

## 4.4 Service / Manager / Validator / Query 命名

同样禁止：

```java
service
query
manager
validator
writer
reader
```

作为依赖字段的默认名称。

应写：

```java
customerService
salesOrderQueryService
productSkuSyncManager
purchaseOrderValidator
priceBatchWriter
financeCounterpartyReader
```

成员变量名称原则：

> 名称描述“它是什么”，而不是只描述“它属于哪种技术角色”。

## 4.5 参数命名

禁止：

```java
void update(Long id, String s, Integer v)
```

推荐：

```java
void update(
        Long productCategoryId,
        String categoryName,
        Integer version)
```

同一方法中不要使用：

```text
id
type
status
value
data
info
item
obj
param
```

这类无法区分业务含义的泛化名称，除非方法上下文已经完全没有歧义。

例如：

```java
require(Long categoryId)
```

优于：

```java
require(Long id)
```

## 4.6 局部变量命名

允许：

```java
var productCategory = ...
var purchaseOrder = ...
var refund = ...
var payment = ...
```

禁止为了使用 `var` 进一步压缩变量名：

```java
var c = ...
var po = ...
var r = ...
var p = ...
```

`var` 的使用原则：

> 可以省略显而易见的类型，不能省略变量表达的业务含义。

## 4.7 缩写规则

允许已经成为正式领域词汇的缩写：

```text
SKU
SPU
UOM
ID
URL
API
DTO
VO
DAO
```

Java 标识符中保持正常 CamelCase：

```java
ProductSkuDao
productSkuDao

ProductSpuDao
productSpuDao

skuId
spuId
apiPermission
```

不要：

```java
productSKUDAO
productSPUDao
SKUId
```

## 4.8 单字母变量

生产代码原则上禁止：

```java
c
r
f
x
o
v
```

允许的有限例外：

```text
i / j
```

仅用于非常短小且明显的索引循环。

```text
e
```

可用于非常短的 catch，但如果异常承担业务语义，仍应写：

```java
duplicateKeyException
validationException
```

Lambda 同样要求有语义：

```java
products.stream()
        .filter(product -> ...)
```

而不是：

```java
products.stream()
        .filter(p -> ...)
```

## 4.9 当前典型整改案例

Before：

```java
private final ProductCategoryDao dao;
private final ProductSpuDao spuDao;

public ProductCategoryEntity require(Long id) {
    var category = dao.selectById(id);
    ...
}
```

After：

```java
private final ProductCategoryDao productCategoryDao;
private final ProductSpuDao productSpuDao;

public ProductCategoryEntity require(Long categoryId) {
    var productCategory = productCategoryDao.selectById(categoryId);
    ...
}
```

注意：

本轮命名整改不得改变业务行为。

## 4.10 自动门禁

Checkstyle 负责：

- MemberName
- LocalVariableName
- ParameterName
- MethodName
- TypeName
- ConstantName

但 Checkstyle 只能检查：

```text
camelCase 是否正确
```

无法判断：

```text
dao
service
r
```

是否语义贫乏。

因此项目自定义 quality guard 额外检查 SCM 正式代码中的高风险泛化成员名：

```text
dao
service
query
manager
validator
repository
mapper
reader
writer
client
```

如果字段类型已经提供明确领域名称，禁止继续使用上述裸名称。

例如：

```java
ProductCategoryDao dao
```

必须报错。

但：

```java
Map<Long, ProductCategoryEntity> categoryById
```

属于正常语义名称。

历史债务采用 baseline / ratchet：

- 现有问题可以暂时登记 baseline
- 新增问题必须为 0
- baseline 只能下降，不能无理由增加

---

# 5. Magic String / Domain Enum

## 5.1 禁止业务 Magic String

禁止：

```java
"ENABLED".equals(status)
"CONFIRMED".equals(status)
"SIGNED".equals(status)
"NORMAL".equals(entryType)
"CUSTOMER".equals(type)
```

必须使用现有 Enum。

例如：

```java
ScmEnableStatusEnum.ENABLED.name()
```

而不是：

```java
"ENABLED"
```

## 5.2 优先复用已有 Enum

当前 SCM 已有大量正式 Enum。

不得重复创建：

```text
EnableStatus
OrderStatus
RefundStatus
ReceiptStatus
FinancePaymentMethod
...
```

应首先查：

```text
common.constant
<domain>.constant
```

## 5.3 新业务枚举要求

如果确实新增：

必须：

- Java Enum
- DB CHECK
- contract test

三者一致。

## 5.4 禁止巨型 Constant 类

不要创建：

```text
ScmConstant.java
```

存全部系统字符串。

按领域拆分。

---

# 6. Permission 治理

## 6.1 不使用 sa-security.yml 作为权限事实源

权限代码：

```text
scm:finance:payment:add
```

属于稳定程序契约，不是环境配置。

不得把权限 vocabulary 搬到 `application.yml / sa-security.yml` 再运行时解析。

否则会造成：

```text
代码 annotation
配置文件
数据库
```

三个事实源。

## 6.2 权限正式结构

采用：

```text
Java Permission Catalog
        ↓
Controller Annotation

DB t_menu
        ↓
Role Permission Mapping

LoginManager
        ↓
Spring Cache
        ↓
Redis / Caffeine
```

现有 `LoginManager` 已经负责：

```text
role
→ menu
→ api_perms
```

并使用 Spring Cache。

不得重新建设第二套权限 Redis。

## 6.3 Permission 按领域拆

例如：

```text
com.xsy.scm.product.permission.ProductPermission
com.xsy.scm.order.permission.OrderPermission
com.xsy.scm.finance.permission.FinancePermission
```

示例：

```java
public final class FinancePermission {

    public static final String RECEIPT_ADD =
            "scm:finance:receipt:add";

    public static final String PAYMENT_ADD =
            "scm:finance:payment:add";

    private FinancePermission() {
    }
}
```

Controller：

```java
@SaCheckPermission(FinancePermission.PAYMENT_ADD)
```

禁止：

```java
@SaCheckPermission("scm:finance:payment:add")
```

## 6.4 自动契约测试

必须增加：

```text
ScmPermissionContractTest
```

检查：

1. SCM Controller 所有 `@SaCheckPermission`
2. 必须来自 Permission Catalog
3. 已发布权限必须存在 `t_menu.api_perms`
4. 不允许重复 / 拼写漂移
5. 已不存在端点的 permission 不应继续错误发布

---

# 7. Utility 使用规范

通用工具优先级：

```text
JDK
↓
Spring Framework
↓
Apache Commons Lang / Collections
↓
XSY Domain Utility
```

例如：

禁止自行实现：

```java
private String trimToNull(...)
```

优先：

```java
StringUtils.trimToNull(...)
```

不要新建：

```text
ScmStringUtils
ScmObjectUtils
ScmCollectionUtils
```

重复包装框架。

允许 XSY Utility 的条件：

它必须包含真正的业务语义。

例如：

```text
ScmDecimalStrings
ScmDocumentNumbers
```

---

# 8. Bean Validation

所有用户输入 Form：

必须提供明确的校验 message。

例如：

```java
@NotNull(message = "客户不能为空")
private Long customerId;

@Size(
    max = 500,
    message = "备注不能超过500个字符"
)
private String remark;
```

禁止：

```java
@NotNull
private Long customerId;
```

成为新代码默认写法。

因为当前全局异常处理器直接返回：

```text
FieldError.defaultMessage
```

没有 message 会导致客户端看到：

```text
must not be null
size must be between ...
```

## 8.1 Enum 参数

减少：

```java
@Pattern(
    regexp = "ENABLED|DISABLED"
)
```

优先：

- Enum 类型
- 或统一 Enum Validator

不得每个 Form 自己维护状态正则。

---

# 9. Comment / Javadoc

代码注释只解释：

```text
WHY
INVARIANT
TRAP
```

不要解释开发过程。

禁止生产代码注释中出现：

```text
F1-3A
F1-3B
Q27
D-3
Wave 5
本轮
下一阶段
此次提交
设计稿 §xx
测试共多少项
```

这些内容属于：

```text
docs
decisions
ADR
git history
```

## 9.1 Javadoc

需要保留 Javadoc 的对象：

- public API
- public shared component
- 非直观业务不变量
- 并发 / 事务约束
- 易误改的架构边界

普通 private method：

如果代码已经表达意图，不强制 Javadoc。

---

# 10. Import / Formatting

禁止 wildcard import：

```java
import xxx.*;
```

全部显式 import。

统一：

- UTF-8
- spaces
- 4 space indent
- LF
- trailing whitespace = 0
- newline at EOF
- Java 最大行宽 120

使用机器格式化。

不要依赖人工格式。

---

# 11. Service 职责

不以：

> “Service 超过 300 行就拆”

作为机械规则。

但：

```text
Service > 400 行
```

必须执行职责审查。

Application Service 负责：

```text
事务编排
状态转换
调用 Domain Policy
调用 DAO
```

不应同时承担大量：

```text
字符串解析
DTO Mapping
复杂计算
状态判断
快照生成
权限规则
SQL 规则
```

可抽取：

```text
Validator / Policy
Assembler / Mapper
Calculator
Manager
SourceReader
```

但禁止为了：

> “一个方法一个类”

过度拆分。

---

# 12. Architecture Rule

引入 ArchUnit。

至少建立：

```text
Controller
    ↓
Service
    ↓
Manager / Policy
    ↓
DAO
```

限制：

```text
Controller 不直接依赖 DAO

Domain Service 不依赖 Controller

common 不反向依赖 product/order/finance

Finance 不写 Order/Purchase/Inventory 业务表

业务域不得依赖 test package

禁止 domain A 随意访问 domain B DAO
```

跨域访问必须：

- 明确的 read-only source DAO
- 或正式 Domain API

---

# 13. 自动质量工具

第一阶段引入：

```text
.editorconfig
Spotless
Checkstyle
ArchUnit
project-specific quality guard
```

Spotless：

负责：

- formatting
- removeUnusedImports
- forbidWildcardImports
- whitespace

Checkstyle：

负责：

- 命名
- import
- line width
- 类结构
- 基础代码规范

ArchUnit：

负责：

- 分层
- package dependency
- package namespace
- cross-domain boundary

项目自定义 guard：

负责：

- SCM magic domain strings
- raw `@SaCheckPermission`
- stage comments
- old SCM package prefix
- forbidden utility duplicates
- 语义贫乏成员变量名

SpotBugs：

作为第二阶段开启，先建立 baseline，不得第一天把几千条历史 warning 全部变成 blocker。

P3C：

作为：

- Java 规则参考
- IDEA 开发辅助

第一阶段不直接把旧 P3C PMD 全量变成 Java 21 CI blocker。

---

# 14. Quality Ratchet

原则：

```text
历史债务允许 baseline
新债务 = 0
```

禁止：

为了让门禁通过：

- 无限扩大 exclude
- 把问题全放 suppression
- 修改测试绕过
- 降低检查等级

Baseline：

只能减少，不得无说明增加。

---

# 15. 文档治理

当前问题：

```text
AGENTS.md      > 2500 行
progress.md    > 2400 行
decisions.md   > 1100 行
```

必须收口。

## 15.1 根目录最终保留

```text
README.md
AGENTS.md
CLAUDE.md
CONTRIBUTING.md
LICENSE
NOTICE.md

Dockerfile*
docker-compose.yml
nginx.conf

.env.example
.git*

docs/
tools/
deploy/

xsy-scm-server/
xsy-scm-web/
xsy-scm-miniapp/
project-reference-examples/
```

## 15.2 迁移

```text
SMARTADMIN_REFERENCE_RULES.md
→ docs/architecture/smartadmin-foundation.md

PROPOSAL-2026-09-18-团队技术提升方案.md
→ docs/archive/proposals/
```

## 15.3 docs 新结构

```text
docs/
├── README.md
├── status.md
├── architecture/
├── requirements/
├── adr/
├── quality/
├── plan/
│   └── active/
├── runbooks/
├── test-report/
└── archive/
```

## 15.4 AGENTS.md

目标：

只保留稳定规则。

不记录：

- 每阶段测试数
- 每次提交
- Finance 开发流水
- 已完成历史

推荐控制：

```text
500 行以内
```

## 15.5 progress

不再无限追加。

`status.md` 只表示：

```text
当前完成
当前进行
当前暂停
当前风险
下一步
```

历史：

依赖 Git，或归档：

```text
docs/archive/progress/
```

## 15.6 decisions

逐步拆 ADR：

```text
docs/adr/
001-postgresql-no-fk.md
002-order-settlement-source.md
003-delivery-sign-receivable.md
004-finance-red-receivable.md
...
```

`decisions.md` 最终只做：

```text
索引 + 当前生效状态
```

---

# 16. 脚本目录

仓库已经存在：

```text
tools/
```

因此：

不要再新增：

```text
script/
scripts/
```

形成第二套脚本目录。

统一：

```text
tools/
```

建议进一步：

```text
tools/
├── quality/
├── migration/
├── e2e/
├── data/
└── verify.py
```

---

# 17. 执行阶段

## Q0 — Quality Baseline

只建立：

- 本文
- editorconfig
- formatter
- checkstyle
- ArchUnit
- quality guard
- baseline
- CI / verify 接入

不改业务逻辑。

---

## Q1 — Package Namespace Migration

只做：

```text
net.lab1024.sa.admin.module.scm
→
com.xsy.scm
```

不要同时：

- 改 Service
- 改 Enum
- 改业务
- 改 SQL 逻辑
- 改 DTO

Package rename 单独 commit。

随后单独 formatter commit。

---

## Q2 — Type Safety / Permission / Validation / Utility / Naming

依次治理：

1. Naming
2. Magic String
3. Enum
4. Permission Catalog
5. Bean Validation message
6. duplicate utility

每一个领域单独 commit。

建议顺序：

```text
common
product
customer
supplier
pricing
order
purchase
inventory
sorting
delivery
finance
report
```

---

## Q3 — Comments / Architecture / Service

清理：

- 阶段性注释
- 巨型 Service
- 重复 assembler
- 重复 validator
- 层级越界

每次只处理一个 domain。

---

## Q4 — Repository / Docs

最后整理：

- root
- docs
- AGENTS
- decisions
- progress/status
- tools

不在 Java 大重构同时移动全部文档，避免 review diff 失控。

---

# 18. 最终质量验收

必须达到：

```text
0 wildcard import（XSY SCM）
0 raw @SaCheckPermission("scm:")
0 新业务 magic status/type/source literals
0 新增语义贫乏依赖字段名（dao/service/query/...）

net.lab1024.sa.admin.module.scm
生产代码 = 0

com.xsy.scm
成为唯一 SCM 正式业务包

所有新增 Form 约束有可读 message

Permission annotation ↔ catalog ↔ DB
contract PASS

ArchUnit PASS
Spotless PASS
Checkstyle PASS

migration checksum PASS
backend full regression PASS

无新增业务功能
```

整改完成后才能恢复：

```text
F1-3C
F1-4
```

---

# 19. Q0 审计任务

Q0 第一轮只做：

```text
质量规则
工具
baseline
审计
计划
```

绝对禁止：

- Package rename
- 批量 Enum 替换
- 批量 Form message
- Service 拆分
- 文档大搬家
- Finance 继续开发
- 新业务 API
- 新 migration
- 新 business decision

审计报告：

```text
docs/quality/java-quality-audit-2026-09-26.md
```

必须输出真实数字：

- SCM Java 文件数量
- Enum 数量
- magic string 命中
- raw permission 数量
- validation no-message 数量
- wildcard imports 数量
- stage comment 数量
- >400 行 Service 数量
- package rename 影响文件数
- MyBatis namespace 影响数
- 文档体量
- 裸 dao/service/query/manager/validator 成员变量数量
- 单字母成员变量数量
- 可明显改成完整领域名称的缩写字段数量

---

# 20. 核心工程原则

最终统一为：

> **包名要短，变量名不能短。**

也就是：

```text
基础设施前缀压缩
+
业务语义名称完整
```

例如：

```java
package com.xsy.scm.product.service;

private final ProductCategoryDao productCategoryDao;
private final ProductSpuDao productSpuDao;
```

优于：

```java
package net.lab1024.sa.admin.module.scm.product.service;

private final ProductCategoryDao dao;
private final ProductSpuDao spuDao;
```
