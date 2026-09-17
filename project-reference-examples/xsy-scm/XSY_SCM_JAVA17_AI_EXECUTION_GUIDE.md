# XSY-SCM Java17 + Vue3 TS + xsy-app 二次开发初始化执行规程

> 用途：将本文件交给 Codex / Claude Code / Cursor / 其他编码 Agent，要求其按步骤完成 XSY-SCM 项目的初始化、运行验证和业务二开准备。
>
> 项目目标：基于官方 SmartAdmin v3.30.0（本项目 XSY-SCM）的 Java17 + Spring Boot 3 版本，使用 Vue3 + TypeScript 管理后台，并加入 xsy-app（UniApp Vue3）作为移动端 / H5 / 小程序基础，在此基础上开发商品、客户、供应商、订单、采购、库存、分拣、配送、财务、营销、溯源等业务模块。
>
> 核心原则：**先完整跑通官方基线，再开始二开；不允许边初始化边大规模重构。**

---

# 1. 技术基线

本项目固定采用以下技术方案：

## 后端

- Java 17
- Spring Boot 3
- MyBatis-Plus
- Sa-Token
- Maven
- MySQL 8
- Redis

使用 XSY-SCM 中的：

```text
xsy-scm-server
```

禁止使用：

```text
smart-admin-api-java8-springboot2
```

---

## PC 管理后台

使用：

```text
xsy-scm-web
```

技术栈：

```text
Vue 3
TypeScript
Vite
Pinia
Ant Design Vue
```

禁止切换到 JavaScript 版本。

---

## 移动端

使用：

```text
xsy-app
```

技术栈：

```text
UniApp
Vue 3
Uni UI
```

目标支持：

```text
H5
微信小程序
Android App
iOS App
```

第一阶段优先保证 H5 正常运行。

---

# 2. SmartAdmin 基线版本

优先使用正式发布版本，不直接基于持续变化的 master 分支二开。

推荐基线：

```text
SmartAdmin v3.30.0
```

如当前仓库存在更新的正式稳定 Release，则：

1. 先检查最新正式 Release。
2. 不使用未经正式发布的开发分支作为生产项目基线。
3. 若需要升级版本，必须先说明版本差异和升级影响。
4. 未经允许不得自行升级 SmartAdmin 大版本。

---

# 3. 初始化目标目录

项目建议保持单仓库结构。

最终目录建议：

```text
supply-chain-system/
├── xsy-scm-server/
├── xsy-scm-web/
├── xsy-app/
├── 数据库SQL脚本/
├── docs/
│   ├── requirement/
│   ├── database/
│   ├── api/
│   └── architecture/
├── deploy/
│   ├── docker/
│   ├── nginx/
│   └── sql/
├── AGENTS.md
├── README.md
└── XSY_SCM_INIT.md
```

暂时禁止拆成多个 Git 仓库。

原因：

- AI 可以同时理解 Java、Vue、UniApp、SQL。
- 方便统一修改接口。
- 方便业务联调。
- 方便后续统一部署。

---

# 4. 第一阶段：检查当前开发环境

开始任何修改之前，先执行环境检查。

需要检查：

```bash
java -version
mvn -version
node -v
npm -v
git --version
```

Java 必须为：

```text
Java 17
```

Maven 必须实际运行在 Java17 上。

如果存在多个 JDK：

- 不删除已有 JDK。
- 优先通过项目级配置指定 Java17。
- IDEA Project SDK 使用 JDK17。
- Maven Runner 使用 JDK17。

检查 MySQL：

```text
MySQL 8.x
```

检查 Redis 是否存在。

如果本地不存在 Redis，可优先通过 Docker 启动。

---

# 5. 拉取 SmartAdmin 官方源码

如果当前目录不存在项目，则执行：

```bash
git clone https://gitee.com/lab1024/smart-admin.git
cd smart-admin
```

然后检查：

```bash
git status
git branch -a
git tag
```

切换正式稳定版本：

```bash
git checkout v3.30.0
```

如果该 Tag 不存在：

- 不自行猜测版本号。
- 查看仓库已有 Tag / Release。
- 选择最近的正式稳定版本。
- 在执行日志中说明实际采用版本。

---

# 6. Git 基线处理

如果项目后续要推送到公司仓库：

推荐保留：

```text
origin
→ 公司自己的项目仓库

upstream
→ SmartAdmin 官方仓库（https://gitee.com/lab1024/smart-admin.git）
```

示例：

```bash
git remote rename origin upstream
git remote add origin <公司仓库地址>
```

不得在不知道公司仓库地址的情况下编造 Git 地址。

开发分支建议：

```text
main/master
develop
feature/product
feature/customer
feature/supplier
feature/order
feature/purchase
feature/inventory
feature/sorting
feature/delivery
feature/finance
```

当前初始化阶段只需要保证：

```text
baseline
develop
```

概念清晰即可。

---

# 7. 不要立即删除官方示例

初始化阶段必须保留 SmartAdmin 官方现有示例页面和模块。

原因：

后续 AI 开发时，需要参考现有代码：

```text
Controller 风格
Service 风格
Manager 风格
DAO 风格
Entity 风格
DTO / VO 风格
Vue 页面结构
API 调用方式
Table 风格
Modal 风格
Form 风格
权限控制方式
菜单配置方式
```

禁止：

```text
初始化时删除大量 Demo
初始化时统一改目录
初始化时改全局组件风格
初始化时换 UI 框架
初始化时引入新的 ORM
```

---

# 8. 初始化数据库

推荐创建独立业务数据库：

```sql
CREATE DATABASE supply_chain
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

数据库名称如果当前项目已有约定，则使用现有名称，不要强制修改。

然后：

1. 找到 SmartAdmin 官方数据库 SQL。
2. 导入基础表结构。
3. 确认导入成功。
4. 禁止直接修改官方系统表结构来承载业务数据。

业务数据后续必须使用独立业务表。

---

# 9. 配置后端

进入：

```text
xsy-scm-server
```

首先阅读：

```text
pom.xml
application.yml
application.yaml
application-dev.yml
application-dev.yaml
```

以及实际存在的配置文件。

禁止根据经验凭空假设配置路径。

找到：

```text
MySQL
Redis
环境 profile
端口
文件上传
日志
```

相关配置。

修改为本地开发环境。

数据库：

```text
host: localhost
port: 3306
database: supply_chain
username: <本地数据库账号>
password: <本地数据库密码>
```

Redis：

```text
host: localhost
port: 6379
```

如果密码为空，则不要凭空添加 Redis 密码。

---

# 10. 启动 Java17 后端

优先使用 IDEA 启动。

同时检查 Maven 编译：

```bash
mvn clean compile
```

如果项目支持：

```bash
mvn spring-boot:run
```

也可以用于验证。

必须完成以下检查：

```text
1. 项目能够编译
2. Spring Boot 能正常启动
3. 数据库连接正常
4. Redis 连接正常
5. 无持续异常日志
6. 登录接口正常
7. 菜单接口正常
8. 权限接口正常
```

如启动失败：

必须先定位现有错误。

禁止为了“跑起来”：

```text
删除依赖
大规模降版本
关闭安全校验
注释核心 Bean
替换框架
```

---

# 11. 初始化 Vue3 TypeScript 管理后台

进入：

```text
xsy-scm-web
```

首先检查：

```text
package.json
package-lock.json
pnpm-lock.yaml
yarn.lock
```

包管理器规则：

```text
有 pnpm-lock.yaml
→ 优先 pnpm

有 package-lock.json
→ 优先 npm

有 yarn.lock
→ 优先 yarn
```

禁止无理由混用多个包管理器。

安装依赖。

例如：

```bash
pnpm install
```

或：

```bash
npm install
```

然后运行：

```bash
pnpm dev
```

或：

```bash
npm run dev
```

根据项目实际 scripts 执行。

---

# 12. 管理后台验证

必须验证：

```text
登录页
首页
菜单
员工管理
角色管理
权限管理
字典
日志
文件
至少一个完整 CRUD 页面
```

然后验证：

```text
前端 API 地址
后端跨域
Token
登录状态
菜单加载
按钮权限
```

全部正常后才进入业务开发。

---

# 13. 初始化 xsy-app

进入：

```text
xsy-app
```

首先阅读：

```text
package.json
manifest.json
pages.json
vite.config.*
.env*
src/
```

以及项目实际配置。

禁止先改页面。

先确认官方 xsy-app 可以运行。

推荐开发顺序：

```text
第一步：H5
第二步：微信小程序
第三步：Android
第四步：iOS
```

初始化阶段只强制要求：

```text
H5 正常启动
登录正常
API 请求正常
Token 正常
基础页面正常
```

---

# 14. xsy-app 的产品定位

xsy-app 不是简单的“PC 后台手机版”。

本项目后续存在两类移动端用户：

```text
客户
员工
```

客户功能：

```text
首页
商品分类
商品搜索
商品详情
购物车
下单
订单
再次购买
账期支付
货到付款
在线支付
余额
优惠券
```

员工功能：

```text
订单查看
订单改价
订单编辑
采购
收货
入库
分拣
称重
配送
扫码
```

第一阶段使用同一个 xsy-app 工程。

后续通过：

```text
用户角色
权限
菜单
路由
```

控制不同用户看到的功能。

未出现明确必要性之前，不拆成两个 UniApp 工程。

---

# 15. 初始化完成后创建基线

当以下三项全部正常运行：

```text
Java 后端
Vue3 TS 管理后台
xsy-app H5
```

创建一个干净基线提交。

建议 Commit：

```text
chore: initialize xsy-scm java17 baseline
```

建议 Tag：

```text
baseline-smartadmin-v3.30.0
```

如果实际 SmartAdmin 版本不是 3.30.0，则 Tag 使用实际版本。

这个基线必须满足：

```text
没有供应链业务代码
没有无关重构
可以完整运行
```

---

# 16. 二开架构原则

禁止修改 SmartAdmin 原有基础能力来实现业务。

应保持：

```text
SmartAdmin 系统能力
+
业务模块
```

SmartAdmin 基础能力继续负责：

```text
登录
员工
角色
菜单
权限
部门
字典
文件
日志
安全
```

业务功能独立实现。

---

# 17. 业务模块规划

建议业务模块：

```text
business/
├── product/
├── customer/
├── supplier/
├── marketing/
├── order/
├── purchase/
├── inventory/
├── sorting/
├── delivery/
├── finance/
└── traceability/
```

如果 XSY-SCM 当前 Java17 项目已有推荐的业务模块目录结构，则：

优先遵循 XSY-SCM 当前工程结构。

不要为了完全匹配上述目录而破坏现有 Maven Module / Package 结构。

---

# 18. 第一阶段业务：商品中心

业务开发第一阶段只做商品领域。

包含：

```text
商品分类
商品
SKU
规格
商品图片
供应商商品
客户价格
协议价
时价
```

需求包括：

```text
三级分类
批量修改
批量上传
不同类型客户不同价格
商品时价
协议价
标品
非标品
多规格
多供应商
自采
供应商配送
默认采购员
默认供应商
```

禁止把所有逻辑直接堆入 Product 表。

至少需要考虑：

```text
Product
ProductSku
ProductCategory
ProductImage
ProductSupplier
ProductPrice
```

实际表结构先设计，再编码。

---

# 19. 第二阶段业务：客户与供应商

客户领域包括：

```text
Customer
CustomerType
CustomerContact
CustomerAddress
CustomerCredit
CustomerPrice
```

必须考虑：

```text
客户分类
集团客户
下属单位
统一结算
独立采购
指定商品可见
指定商品屏蔽
业务员
供应商绑定
账期金额限制
账期时间限制
```

供应商领域至少考虑：

```text
Supplier
SupplierContact
SupplierProduct
```

---

# 20. 第三阶段业务：订单

订单不能设计成简单 CRUD。

至少考虑：

```text
Order
OrderItem
OrderAddressSnapshot
OrderPriceSnapshot
OrderOperationLog
Refund
ReturnOrder
```

必须考虑：

```text
商城下单
后台录单
补单
订单核算
实际称重
改价
退款
退货
订单操作日志
异常订单
```

关键数据需要快照。

禁止订单历史价格直接关联实时商品价格。

---

# 21. 第四阶段业务：采购

目标链路：

```text
Order
↓
PurchaseDemand
↓
需求汇总
↓
按供应商 / 采购员 / 品类拆分
↓
PurchaseOrder
↓
PurchaseOrderItem
↓
Receiving
```

需要支持：

```text
自动采购汇总
采购任务
二维码分享
采购单价
采购重量
多次收货
实重收货
```

---

# 22. 第五阶段业务：库存

库存属于核心模块。

禁止只维护：

```text
product.stock
```

必须采用：

```text
库存余额
+
库存流水
```

推荐概念：

```text
Inventory
InventoryMovement
InventoryBatch
```

至少覆盖：

```text
采购入库
销售出库
退货入库
报损
报溢
盘点
规格转换
库存预警
```

必须保留完整库存变动流水。

---

# 23. 成本核算

需求包含：

```text
加权平均进价
```

因此库存模块设计时必须保留：

```text
入库数量
入库成本
库存数量
库存金额
平均单位成本
```

任何修改库存余额的操作都必须通过统一库存业务层。

禁止不同模块自行 UPDATE 库存。

---

# 24. 第六阶段业务：分拣

至少规划：

```text
SortingTask
SortingTaskItem
SortingRecord
WeightRecord
```

需要支持：

```text
按商品分拣
按客户订单分拣
实际重量
打印
称重上传
非标品
误差阈值
标品一键分拣
分拣员筛选
线路筛选
供应商代分拣
```

设备接入暂时通过接口抽象。

不要在第一版直接写死某品牌电子秤。

---

# 25. 第七阶段业务：物流配送

规划：

```text
DeliveryRoute
DeliveryTask
DeliveryOrder
Driver
Vehicle
Track
```

需要考虑：

```text
客户区域
配送时间
订单数量
订单金额
配送线路
订单地图分布
轨迹
发货单打印
```

第一版路线规划允许人工配置。

智能自动规划属于后续增强。

---

# 26. 第八阶段业务：财务

规划：

```text
应收
已收
待收
应付
利润
客户利润
商品利润
销售员业绩
采购明细
```

不要一开始做完整财务软件。

第一阶段目标是：

```text
业务财务
```

不是：

```text
会计总账系统
```

---

# 27. 第九阶段：商城

必须在商品、客户、订单基础模型稳定后再正式开发商城。

商城顺序：

```text
首页
分类
搜索
商品详情
购物车
确认订单
提交订单
支付
订单列表
订单详情
再来一单
```

然后逐步增加：

```text
常购商品
优惠券
满减
满赠
限时特价
余额
账期
货到付款
在线支付
```

---

# 28. 第十阶段：溯源

溯源需求：

```text
一物一码
扫码溯源
```

建议后续设计：

```text
TraceCode
TraceBatch
TraceEvent
```

溯源记录要能够关联：

```text
商品
批次
采购
入库
分拣
配送
```

---

# 29. AI 编码规则

AI 每次开发业务模块前必须执行：

```text
1. 阅读 XSY-SCM 当前同类型模块。
2. 总结当前项目编码规范。
3. 查找可复用组件。
4. 查找已有工具类。
5. 查找已有依赖。
6. 设计数据库。
7. 设计 API。
8. 再开始编码。
```

禁止：

```text
不看项目就创建新架构
重复造分页工具
重复造权限框架
重复造异常体系
重复造 API Result
重复造 Bean Copy
重复造日期工具
重复造 ID 生成器
```

---

# 30. Java 开发规则

优先复用当前项目依赖。

当项目已有以下工具时必须优先使用：

```text
Lombok
MyBatis-Plus
MapStruct
BeanUtils
Hutool
Apache Commons
Sa-Token
Validation
```

具体是否存在必须查看 pom.xml。

禁止因为个人偏好重复加入同类库。

例如：

```text
已有 Hutool
→ 不再因为一个 String 工具引入 Guava

已有 MapStruct
→ Converter 优先 MapStruct

已有 Lombok
→ DTO / VO / Entity 按现有规范使用 Lombok
```

---

# 31. 前端开发规则

新增页面前必须找到至少一个 XSY-SCM 原生相似页面作为模板。

例如：

```text
列表页
→ 参考现有列表

新增/编辑
→ 参考现有 Form / Modal

详情
→ 参考现有详情页面

字典
→ 使用已有字典能力

权限按钮
→ 使用现有权限机制
```

禁止：

```text
新增另一套 UI 框架
自行包装第二套 request
自行创建第二套 Table 组件
绕过现有权限系统
```

---

# 32. 数据库规则

每张业务表必须明确：

```text
主键
业务编号
状态
创建时间
更新时间
创建人
更新人
逻辑删除
```

是否全部需要上述字段，以 XSY-SCM 当前数据库规范为准。

同时必须考虑：

```text
唯一索引
普通索引
组合索引
外键关系
并发
幂等
历史快照
```

默认不要依赖 MySQL 物理外键约束。

关系由业务层维护。

---

# 33. 状态字段规则

订单、采购、退款、库存、配送等状态：

禁止使用：

```text
0
1
2
3
```

散落在业务代码中。

必须：

```text
Enum
+
数据库状态字段
```

并保持前后端统一语义。

---

# 34. 金额规则

所有金额：

禁止 Java 使用：

```text
double
float
```

必须使用：

```text
BigDecimal
```

数据库：

```text
DECIMAL
```

具体精度按照业务确定。

---

# 35. 重量规则

项目涉及大量实重业务。

重量禁止使用浮点数随意累计。

Java 优先：

```text
BigDecimal
```

数据库：

```text
DECIMAL
```

必须统一：

```text
kg
g
斤
```

中的内部标准单位。

推荐内部统一使用：

```text
kg
```

展示层再转换。

实际规则应写入项目文档。

---

# 36. 删除规则

涉及以下数据：

```text
订单
采购
库存流水
退款
配送
财务
```

原则上不允许物理删除。

通过：

```text
状态
作废
冲销
逻辑删除
```

管理。

特别是库存流水和财务记录必须保留审计能力。

---

# 37. 操作日志

以下操作必须考虑审计：

```text
订单改价
订单取消
退款
采购改价
收货
库存调整
盘点
报损
报溢
客户账期修改
客户价格修改
配送状态修改
```

优先复用 SmartAdmin 自带日志体系。

必要时额外建立业务操作日志。

---

# 38. API 设计规则

业务 API 应分领域。

示例：

```text
/api/product/**
/api/customer/**
/api/supplier/**
/api/order/**
/api/purchase/**
/api/inventory/**
/api/sorting/**
/api/delivery/**
```

实际路由风格优先遵循 XSY-SCM 当前项目约定。

禁止突然引入完全不同 REST 风格。

---

# 39. 开发节奏

每一个模块都按以下顺序：

```text
需求确认
↓
现有代码调查
↓
数据模型
↓
数据库迁移 / SQL
↓
后端 API
↓
单元 / 基础测试
↓
PC 管理后台
↓
xsy-app（如果需要）
↓
联调
↓
测试
↓
提交
```

禁止一次让 AI 同时生成所有业务模块。

---

# 40. 每次提交前检查

至少执行：

## Java

```bash
mvn clean compile
```

若项目存在测试：

```bash
mvn test
```

---

## Vue

执行项目实际存在的：

```bash
npm run build
```

或：

```bash
pnpm build
```

以及：

```text
lint
type-check
```

如果 package.json 中存在。

---

## xsy-app

至少确保：

```text
H5 编译成功
无 TypeScript 错误
无明显运行时异常
```

---

# 41. 禁止事项

AI 未经明确允许禁止：

```text
禁止改 Java17 为其他版本
禁止改 Spring Boot 大版本
禁止从 MyBatis-Plus 改成 JPA
禁止从 Vue3 改 React
禁止更换 Ant Design Vue
禁止拆微服务
禁止引入 Spring Cloud
禁止拆多个仓库
禁止改认证体系
禁止替换 Sa-Token
禁止删除 XSY-SCM 核心模块
禁止大规模重命名
禁止无理由升级全部 npm 依赖
禁止无理由升级 Maven 依赖
禁止一次性重构整个项目
```

---

# 42. 对第三方依赖的原则

新增 Maven / npm 依赖前必须回答：

```text
1. 当前项目是否已有同类能力？
2. Java / 浏览器标准库能否解决？
3. XSY-SCM 是否已有工具？
4. 为什么必须新增？
5. 是否长期维护？
6. License 是否允许商用？
7. 是否引入安全风险？
```

没有明显收益，不新增。

---

# 43. Docker / 部署

本地开发第一阶段可以使用：

```text
MySQL
Redis
```

Docker 化。

业务服务是否 Docker 化根据现有部署环境决定。

后续部署结构建议：

```text
Nginx
├── PC 管理后台
├── H5
└── /api
      ↓
Spring Boot
      ↓
MySQL + Redis
```

第一阶段禁止引入：

```text
Kubernetes
Nacos
Gateway
Sentinel
RocketMQ
Kafka
```

除非出现明确业务需求。

---

# 44. 初始化阶段最终验收

完成初始化后，必须输出一份报告。

格式：

```markdown
# XSY-SCM 初始化报告

## 版本
- XSY-SCM:
- Java:
- Spring Boot:
- Node:
- MySQL:
- Redis:

## 后端
- [ ] 编译成功
- [ ] 启动成功
- [ ] 数据库正常
- [ ] Redis 正常
- [ ] 登录正常

## PC
- [ ] 安装成功
- [ ] 启动成功
- [ ] 登录正常
- [ ] 菜单正常
- [ ] 权限正常

## xsy-app
- [ ] 安装成功
- [ ] H5 启动成功
- [ ] API 正常
- [ ] 登录正常

## Git
- 当前分支：
- 当前 commit：
- XSY-SCM 基线：
- 是否已创建 baseline tag：

## 已修改文件

## 遇到的问题

## 尚未完成事项

## 下一步
```

---

# 45. 当前 AI 的第一条执行指令

收到本文件后：

**不要立即编码业务。**

先执行：

```text
步骤 1：
检查当前工作区目录和 Git 状态。

步骤 2：
确认 XSY-SCM 实际版本和目录。

步骤 3：
确认 Java17 后端、Vue3 TS、xsy-app 是否存在。

步骤 4：
检查 Java / Maven / Node / 包管理器环境。

步骤 5：
检查数据库 SQL 和配置文件。

步骤 6：
尝试分别启动：
- Java 后端
- Vue3 TS 管理后台
- xsy-app H5

步骤 7：
修复“阻塞启动”的问题。

步骤 8：
不要进行业务重构。

步骤 9：
全部启动成功后创建基线。

步骤 10：
输出《XSY-SCM 初始化报告》。
```

---

# 46. 初始化完成后的第二条执行指令

只有当初始化验收全部通过后，才能开始业务开发。

第一业务任务：

```text
商品中心
```

在编码之前，先输出：

```text
1. XSY-SCM 当前模块结构分析
2. 商品领域数据模型
3. 表结构草案
4. API 草案
5. PC 页面规划
6. xsy-app 页面规划
7. 与订单 / 采购 / 库存的关系
8. 可能的风险
```

经过现有代码调查后再实施。

---

# 47. 总目标

最终系统目标：

```text
XSY-SCM Java17
        │
        ├── PC 管理后台
        │
        │   ├── 商品
        │   ├── 客户
        │   ├── 供应商
        │   ├── 营销
        │   ├── 订单
        │   ├── 采购
        │   ├── 库存
        │   ├── 分拣
        │   ├── 配送
        │   ├── 财务
        │   └── 溯源
        │
        └── xsy-app
            ├── 客户商城
            └── 员工业务端
```

业务主链：

```text
商品
↓
客户定价
↓
订单
↓
采购
↓
收货
↓
库存
↓
分拣
↓
配送
↓
结算
```

所有技术设计优先服务于这条主链。

---

# 48. AI 执行原则

执行过程中：

```text
先调查
再修改

先复用
再新增

先跑通
再优化

先单体
再考虑拆分

先保证数据正确
再追求页面效果

先完成核心链路
再开发高级功能
```

如发现本文件描述与当前 XSY-SCM 实际代码不一致：

**以当前仓库实际代码为准，并在执行报告中说明差异。**

不得为了符合本文件而强行修改官方项目结构。

