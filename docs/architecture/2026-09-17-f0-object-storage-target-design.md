# F0 Object Storage Activation · Target Design（目标设计）

日期：2026-09-17。基线提交：`663d35442f8105dcde86201550de38bfc7494b3d`（W5.5 已完成）。
本文件对应人类裁决（2026-09-17，D1–D9），是本波次的范围与验收依据。

---

## 0. 本文件的状态与前置裁决

### 0.1 定位

实施授权已生效。后续人类指令明确：继续完成实现，测试交给他人，本执行者不再运行测试。
因此代码与验收资产交付后保持 **FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED**，
不以交付替代 F0-3 真实集成/F0-4 全量回归。结果见
[F0 验收报告](./2026-09-17-f0-object-storage-验收报告.md)。


F0 是**基础设施波次**，不是业务波次。它只做一件事：

```text
把已经存在的 SmartAdmin 文件中心，从 Local Storage 激活为 S3-Compatible 对象存储，
并顺手关闭其既有的 fileKey 直读缺口。
```

F0 **不新增任何 SCM 业务域、不新增任何业务附件表、不进入 W6**。

F0 的上游依据：

- [文件存储与附件体系设计 评审报告](./2026-09-17-file-storage-design-review.md) — 现状核对、阻塞项与 8 项待决策
- [SmartAdmin 原生功能同步报告](./2026-09-17-smartadmin-native-feature-sync-report.md) — W5.5 结论（文件域零改动）
- [SmartAdmin 原生功能一致性审计](./2026-09-17-smartadmin-native-feature-parity-audit.md) — 逐模块对照证据
- [AGENTS.md](../../AGENTS.md) §1 — F0 的状态与边界
- 外部设计《XSY-SCM 文件存储与对象存储激活设计》 — F0 的原始提案（差异见 §17）

### 0.2 前置裁决（**共 9 项，D1–D9，已由人类裁决批准**）

| 裁决 | 内容 | 本文落点 |
| --- | --- | --- |
| D1 | 批准对 `sa-base` 做最小必要修改：`FileConfig`、`FileController` / 文件访问安全、`FileStorageCloudServiceImpl`。所有修改须解释为 PostgreSQL/MinIO/安全兼容需要，不做框架重构。`FileFolderTypeEnum` **本波次不增加 SCM 业务类别**，留 F1 | §9 |
| D2 | `FileConfig` 增加可配置 `path-style-access-enabled`，**默认保持上游行为 `false`**；MinIO 环境变量配置 置 `true`；`S3Client` 与 `S3Presigner` 必须使用同一配置 | §5 |
| D3 | Public/Private 采用**方案 A**：`public/*` → MinIO Bucket Policy anonymous GET → `public-url-prefix`；`private/*` → 禁止 anonymous → Presigned URL。**MinIO 不依赖 `ObjectCannedACL.PUBLIC_READ` 作为权限事实源**；设计须明确如何避免 MinIO 不支持 canned ACL 导致上传失败 | §4 / §6 |
| D4 | 修 `fileKey.contains("public")` → 严格 `fileKey.startsWith("public/")`。**禁止**给三个接口统一加一个 permission 而破坏 Notice/HelpDoc 等原生调用；须先审计调用者与菜单 permission，再设计 upload / getFileUrl / download 的最小安全门禁。SCM 私有文件从 F1 起必须走业务 API（业务权限 + file ownership/relation 校验 + FileService），禁止只凭 private fileKey 取文件 | §3 |
| D5 | 统一文件大小为：`max-file-size = 20MB`、`max-request-size = 25MB`、`t_config.maxUploadFileSizeMb = 20`、code fallback = 20。**不允许同时保留 10 / 20 / 30 / 50 四套含义**。Tika MIME 检测保持开启。大文件走 Presigned Direct Upload，**不在 F0 扩 scope** | §8 |
| D6 | 已冻结的 `product_image` 继续作为商品图片正式模型，**禁止新增并行 `product_media`**。F1 如需接 `t_file`，用新 migration 给 `product_image` 增 `file_id` 再逐步迁移；**不修改 V6** | §1.2 |
| D7 | F0 不解决全部业务附件类别。F1+ 推荐：SmartAdmin `FileService` 仍是唯一上传入口；**禁止** SCM 直接调 `IFileStorageService` 后自行复制安全检查与 `t_file` 落库。后续采用粗粒度 `SCM_PUBLIC` / `SCM_PRIVATE` + 服务端受控 subPath 评估，不为每种附件无限扩 `FileFolderTypeEnum` | §1.2 / §3.6 |
| D8 | F0 新建 `deploy/minio/compose.yaml`（MinIO、9000 API、9001 Console、持久化 volume、`xsy-scm-dev` bucket bootstrap、`public/*` bucket policy）。**不要求**创建根目录 `docker-compose.yml`。同步修正 AGENTS 仓库结构说明 | §7 / §9.5 |
| D9 | AGENTS 只增加不重复的全局硬规则；MinIO / path style / key format / bucket policy / attachment lifecycle 的细节留在 File Storage / 本 Target Design，**不把同一套规则重复写入 AGENTS.md、SMARTADMIN_REFERENCE_RULES.md、design docs 三处** | §15 |

### 0.3 F0 顺序（固定，不再调整）

```text
F0-0  Security Closure
F0-1  MinIO Compatibility
F0-2  Configuration Alignment
F0-3  Cloud Activation + Real Integration Test
F0-4  Full Regression
```

**F0 验收必须使用 cloud/MinIO 环境变量配置。Local 模式只要求回归通过，不要求通过 Presigned URL 验收。**

---

## 1. 范围

### 1.1 做（Scope）

```text
1  deploy/minio/compose.yaml —— MinIO 服务 + bucket bootstrap + public/* policy
2  FileConfig 增加 path-style-access-enabled（默认 false）+ send-object-acl（默认 true）
3  FileConfig / 配置项全部环境变量化，凭据不落 Git
4  FileStorageCloudServiceImpl：
     getACL 的 contains → startsWith 严格前缀判断
     上传时按 send-object-acl 决定是否发送 ObjectCannedACL
     非法前缀（既非 public/ 也非 private/）的处理口径
5  FileController upload / getFileUrl / downLoad 的最小安全门禁
6  文件大小三处口径统一（multipart / t_config / code fallback）
7  复用 dev profile，以环境变量激活 mode: cloud
8  新增文件域后端测试 + 云端集成测试
9  Playwright：文件域真实上传/下载/公开/私有/拒绝用例
10 AGENTS 仓库结构说明同步修正
```

### 1.2 不做（Non-scope，硬边界）

```text
不重写 FileService
不重建 t_file
不重做文件管理页面（菜单 193 / /support/file/file-list 保持原样）
不新增 SCM 业务附件表：
   product_media / supplier_certificate / customer_attachment /
   purchase_attachment / trace_attachment / finance_attachment
不新增并行商品图片模型（product_image 继续为正式模型，D6）
不修改 V1–V16（只允许追加新 migration）
不增加 FileFolderTypeEnum 的 SCM 业务类别（留 F1，D1）
不实现 Presigned Direct Upload（大文件演进，留后续）
不实现 orphan file cleaner / 临时目录 TTL 清理
不实现 SCM 业务权限与文件归属关系（F1 起）
不实现 SCM 直接调用 IFileStorageService 的旁路（D7 明令禁止）
不改变 local 模式的行为语义（仅要求回归通过）
不进入 W6 Inventory
```

### 1.3 交付物

```text
deploy/minio/compose.yaml                     新增
deploy/minio/.env.example                     新增（真实凭据不入 Git）
xsy-scm-server/sa-base/.../config/FileConfig.java                    修改
xsy-scm-server/sa-base/.../file/service/FileStorageCloudServiceImpl.java  修改
xsy-scm-server/sa-base/.../file/controller/FileController.java       修改
xsy-scm-server/sa-base/.../file/service/FileService.java             修改（仅可见性判定所需）
xsy-scm-server/sa-base/.../securityprotect/service/Level3ProtectConfigService.java  修改（fallback 50→20）
xsy-scm-server/sa-base/src/main/resources/{dev,pre,prod,test}/sa-base.yaml  修改
xsy-scm-server/sa-admin/src/main/resources/db/migration/V17__*.sql    新增（data-only）
xsy-scm-server/sa-admin/src/test/**                                   新增测试
xsy-scm-web/e2e/f0-file-storage.spec.ts                               新增
AGENTS.md                                                             仓库结构说明修正
```

---

## 2. 当前文件流转（Current file flow）

### 2.1 上传链路

```text
POST /file/upload?folder={int}
  FileController.java:44-49
      RequestUser requestUser = SmartRequestUtil.getRequestUser();
      fileService.fileUpload(file, folder, requestUser);
        ↓
  FileService.java:68-117
      :69-72   SmartEnumUtil.getEnumByValue(folder, FileFolderTypeEnum.class)
               null → "文件夹错误"                       ← folder 取值被枚举强制
      :74-86   校验非空、文件名长度 ≤ 100
      :89      securityFileService.checkFile(file)      ← 大小 + Tika MIME
      :95      fileStorageService.upload(file, folderTypeEnum.getFolder())
      :102-111 落 t_file（folder_type / file_name / file_size / file_key / file_type / creator_*）
      :114     回填 fileId
        ↓
  FileStorageCloudServiceImpl.java:86-138（cloud 模式）
      :96      fileKey = path + uuid + "_" + time + "." + ext
      :107     ObjectCannedACL acl = this.getACL(path)
      :116     .acl(acl)                                 ← MinIO 的失败点，见 §4.3
      :129     url = cloudPublicUrlPrefix + fileKey
      :130-133 PRIVATE → url = getFileUrl(fileKey)
```

### 2.2 读取链路

```text
GET /file/getFileUrl?fileKey=...
  FileController.java:51-55 → FileService.java:162-176 → IFileStorageService.getFileUrl
     FileStorageCloudServiceImpl.java:147-184
        :152  if (!fileKey.startsWith("private"))  → 返回 publicUrlPrefix + fileKey
        :158-180 private → Redis 缓存(file:private:{fileKey}) 或 S3 Presigner 生成临时 URL

GET /file/downLoad?fileKey=...
  FileController.java:57-72 → FileService.java:182-194 → IFileStorageService.download
     FileStorageCloudServiceImpl.java:191-217  HeadObject 取元数据 + GetObject 取字节流
```

### 2.3 鉴权链路（决定安全模型）

```text
MvcConfig.java:29-33   AdminInterceptor 注册到 /**，仅排除 Swagger 白名单
AdminInterceptor.preHandle：
   ① StpUtil 取 token → LoginService.getLoginEmployee        → 无登录态则拒绝
   ② @NoNeedLogin 放行；否则登录态必须有效
   ③ administratorFlag == true → 放行（跳过权限校验）
   ④ SaAnnotationStrategy.checkMethodAnnotation(method)      → 只校验方法上的注解
```

**关键事实**：第 ④ 步在方法**没有** `@SaCheckPermission` 时不做任何校验。
`FileController` 三个端点的 `@SaCheckPermission` 计数为 **0**。

因此当前真实口径是：

```text
/file/upload、/file/getFileUrl、/file/downLoad
  = 需要登录，但不需要任何权限
  = 任何已登录员工，只要拿到 fileKey，即可读取任意 private/ 对象
```

（精确表述：不是匿名可读，是**登录即可读**。AdminInterceptor 的第 ①② 步仍然生效。）

### 2.4 调用者审计（D4 要求）

**前端调用点（7 处）**

| 调用方 | 端点 | 传入 folder | 读取者身份 |
| --- | --- | --- | --- |
| `components/framework/wangeditor/index.vue:41` | upload | `COMMON(1)` | 富文本编辑者本人 |
| `components/support/file-upload/index.vue:136` | upload | 由 `props.folder` 决定，默认 `COMMON` | 表单编辑者本人 |
| `components/support/file-upload/index.vue:212` | downLoad | — | 同表单用户（通常=上传者） |
| `components/support/file-preview/index.vue:68` | downLoad | — | 详情页读者（**可能不是上传者**） |
| `components/support/file-preview-modal/index.vue:39,64` | getUrl + downLoad | — | 详情页读者（**可能不是上传者**） |
| `views/support/file/file-list.vue:267` | downLoad | — | 文件管理页使用者 |
| `views/business/scm/product/.../product-image-upload.vue:35` | upload | **硬编码 `1` = COMMON** | 商品维护者本人 |

**folder 分布**

| folder | 值 | 对象前缀 | 使用场景 |
| --- | --- | --- | --- |
| `COMMON` | 1 | `private/common/` | 富文本图片、OA 企业证照、**商品图片**、文件管理页上传 |
| `NOTICE` | 2 | `private/notice/` | 公告附件（`notice-form-drawer.vue:82`） |
| `HELP_DOC` | 3 | `private/help-doc/` | 帮助文档附件 |
| `FEEDBACK` | 4 | `private/feedback/` | 意见反馈附件（`feedback-modal.vue:24`） |

**读取者页面（决定"不能破坏什么"）**

```text
公告详情 / 我的通知          views/business/oa/notice/notice-detail.vue、notice-employee-detail.vue
帮助文档                     views/support/help-doc/**
意见反馈                     views/support/feedback/feedback-list.vue
OA 企业证照                  views/business/oa/enterprise/enterprise-detail.vue
文件管理                     views/support/file/file-list.vue
商品图片                     views/business/scm/product/**（经业务接口取 URL，不走文件端点）
```

**后端调用点（1 处）**

```text
sa-admin/.../system/support/AdminFileController.java:39
    ResponseDTO.ok(fileService.queryPage(queryForm));      ← 仅分页查询
```

**菜单与权限点（D4 要求）**

```text
193 文件管理（页面 /support/file/file-list）  无 perms_type
200   查询   support:file:query                ← 文件域唯一权限点
132 公告管理  185 oa:notice:query / 186 add / 187 update / 188 delete
147 帮助文档  168 support:helpDoc:query / 169 add / 202 update / 201 delete / 170,171 catalog
148 意见反馈  无按钮权限点
149 我的通知  无按钮权限点
```

**角色授予现状**

```text
V3 种子仅 1 个角色：role_id=1 超级管理员（SUPER_ADMIN）
   备注原文：「员工1同时具备 administrator_flag，权限判定不受角色影响」
t_role_menu 共 135 条，全部为 role_id=1
support:file:query（menu 200）已授予 role 1
非管理员角色在种子中不存在
```

**该审计的结论**：当前 V2 基线中唯一存在的账号是**权限校验被绕过**的管理员，
所以"给三个端点加权限"在今天的基线里**不会立刻暴露破坏**；
真正的风险是未来任何非管理员角色都会连带失去 Notice / HelpDoc / Feedback 的附件读取能力。
这正是 D4 要求"不要简单加一个 permission"的原因。

### 2.5 现有测试覆盖（决定 F0 要补什么）

```text
后端：文件域 Java 测试 = 0（find 结果为空）
功能探针：tools/smartadmin_feature_probe.mjs:101  仅 ['file / queryPage', 'POST', '/support/file/queryPage']
Playwright：e2e/smartadmin-native.spec.ts:227-233  仅"文件管理页面加载并成功查询"
```

即 **upload / getFileUrl / downLoad 三个端点目前零测试覆盖**。

---

## 3. 安全模型（Security model，D4）

### 3.1 缺口定义

```text
缺口：/file/getFileUrl 与 /file/downLoad 仅要求登录，不校验可见性、归属或权限。
后果：任何已登录员工只要获得 private fileKey，即可读取任意私有文件。
现状严重度：当前 private/ 下已存在 private/common/（商品图片、OA 企业证照）
            与 private/feedback/，因此缺口不是纯理论。
```

### 3.2 设计约束

```text
约束 1  保持 Notice/HelpDoc 登录可读；Feedback/OA 按 HD-3 严格策略及后续债务验收
约束 2  不新增权限点（避免为原生流程补授权而引入 t_role_menu 迁移）
约束 3  修正必须落在可见性/归属维度，而不是笼统的"接口权限"
约束 4  SCM 私有文件的强保证（业务权限 + 关系校验）属 F1，本波次只做"地板"
```

### 3.3 最小门禁设计

**upload（写入方向）**

```text
保持"登录即可"，不新增权限校验。理由：
  · upload 是写入新对象，不泄漏既有数据；敏感内容由 Tika MIME 白名单 + 大小上限拦截（§8）
  · 原生上传入口分布在 7 个页面，加权限必然破坏（约束 1）
  · folder 取值已被 FileFolderTypeEnum 强制（FileService.java:69-72），前端无法指定任意 path
  · t_file 已记录 creator_id / creator_user_type / creator_name（FileService.java:108-110），具备审计能力
新增（F0-0）：上传时对 folder 枚举值做显式白名单校验（仅允许 1–4），
             并在 path 进入 IFileStorageService 前做前缀合法性断言（见 §3.4）
```

**getFileUrl / downLoad（读取方向）**

```text
第一层：前缀严格化（替代现有宽松判断）
    fileKey 必须以 "public/" 或 "private/" 开头，否则直接拒绝
    这同时修掉 getACL 的 contains 与 getFileUrl 的 startsWith 口径不一致（D4 明确要求）

第二层：可见性判定
    public/   → 放行（公开资产；cloud 下本就匿名可读，见 §4）
    private/  → 进入第三层

第三层：private 归属/权限判定（满足任一即放行）
    a. administratorFlag == true
    b. 当前用户拥有 support:file:query
    c. t_file.creator_id == 当前用户 id（本人上传）
    否则 → 403，返回 UserErrorCode.NO_PERMISSION
```

### 3.4 逐 folder 读取策略表（**本设计的核心，兼顾约束 1**）

```text
前缀                    策略                                        依据
---------------------------------------------------------------------------------------------
public/**               登录即可                                    公开资产
private/common/         a ∨ b ∨ c（严格）                            商品图片、OA 证照；未来非管理员 OA 读者须先完成 F0-DEBT-01
private/notice/         登录即可（豁免）                             公告面向全体员工，员工在"我的通知"读附件
private/help-doc/       登录即可（豁免）                             帮助文档面向全体员工
private/feedback/       a ∨ b ∨ c（严格）                            反馈附件仅提交者与管理员可见
其他前缀                 拒绝                                         防止绕过前缀判断
```

**豁免 private/notice/ 与 private/help-doc/ 的理由**：这两个目录的原生读者是
**普通员工**（菜单 149 我的通知、帮助文档），他们在种子中没有任何权限点，
若施加严格策略则必须为其补授权（违反约束 2）或改原生行为（违反约束 1）。
豁免后这两个目录仍受"登录"约束，只是不再受归属约束 —— 这是一个**显式的、有记录的取舍**，
见 §16 人类决策 HD-1。

**为什么对 private/common/ 施加严格策略不会破坏商品图片显示**：
商品图片的 URL 由**业务接口**在服务端解析（`ProductQueryService.enrich():58-59,74`
调用 `FileService.getFileList()` → `IFileStorageService.getFileUrl()`），
**不经过 `/file/getFileUrl` HTTP 端点**，因此 Controller 层门禁不影响它。
这正好就是 D4 要求的 F1 模式（业务权限 → 关系校验 → FileService）。

### 3.5 实现落点

```text
FileController.java
    getUrl()     → 新增可见性门禁调用
    downLoad()   → 新增可见性门禁调用
    upload()     → 仅新增 folder 白名单断言（可选，建议）

新增（建议放在 scm/common 之外的底座层，供 Controller 与未来业务层复用）
    FileAccessGuard  —— 输入 (fileKey, RequestUser)，拒绝时由 Controller 返回 HTTP 403 + ResponseDTO(NO_PERMISSION)
        职责：前缀严格校验 + 可见性判定 + 归属/权限判定 + 逐 folder 策略表

FileStorageCloudServiceImpl.java
    getACL()  → contains("public") 改为严格前缀判定（D4）
```

**门禁必须复用 `FileService` 已有的 `t_file` 查询能力**（`FileDao.getByFileKey`），
不新增 DAO、不新增表。

### 3.6 F1 起的强保证（本波次只定义，不实现）

```text
SCM 私有文件禁止只凭 private fileKey 获取。F1 起必须：

    Business Controller
        ↓ @SaCheckPermission("scm:<domain>:query")
        ↓ 关系校验（该 fileId 是否属于本次请求的业务对象）
        ↓ FileService（服务端解析 URL / 流）
        ↓ 返回 Presigned URL

并按 D7：SCM 不得直接调用 IFileStorageService 后自行复制安全检查与 t_file 落库。
粗粒度 SCM_PUBLIC / SCM_PRIVATE + 服务端受控 subPath 的评估在 F1 进行，
不为每种附件无限扩 FileFolderTypeEnum。
```

---

## 4. Public / Private 访问模型（D3，方案 A）

### 4.1 职责划分

```text
public/*                       private/*
──────────────────────────     ──────────────────────────
Bucket Policy: anonymous GET    Bucket Policy: 无 anonymous 授权（默认拒绝）
URL = public-url-prefix + key   URL = Presigned GET URL（短时）
缓存：不需要                    缓存：Redis file:private:{bucket}:{endpoint}:{TTL}:{fileKey}；TTL > 5s 时缓存 expire-5s，短 TTL 不缓存
                                 （RedisKeyConst.java:18）
```

### 4.2 public 访问事实源 = Bucket Policy，不是 canned ACL

**MinIO 不支持 `ObjectCannedACL.PUBLIC_READ` 作为权限事实源**（D3）。
因此 `public/` 的可匿名读取**必须**由 Bucket Policy 表达（§6），
而不是依赖对象自身的 ACL。

`ObjectCannedACL` 在 F0 之后只承担**一个内部角色**：
告诉 `FileStorageCloudServiceImpl` 该 fileKey 应当返回"公网直链"还是"临时 URL"。

```text
getACL(path) 的返回值
    用途一（保留）：决定 upload 返回的 fileUrl 形态与 getFileUrl 的分支
    用途二（F0 取消）：不再作为 S3 权限事实源
```

### 4.3 如何避免 MinIO 不支持 canned ACL 导致上传失败（D3 明确要求）

**风险点**：`FileStorageCloudServiceImpl.java:116` 无条件发送 `.acl(acl)`。
MinIO 对对象级 canned ACL 的支持有限，`PUBLIC_READ` 可能导致
`PutObject` 返回 `NotImplemented` / `AccessDenied`，**上传直接失败**。

**设计**：新增配置 `file.storage.cloud.send-object-acl`（布尔）

```text
send-object-acl = true    （默认，保持上游与云厂商行为）
      → PutObjectRequest 照常携带 .acl(acl)

send-object-acl = false   （MinIO 环境变量配置 使用）
      → 不调用 .acl(...)，PutObjectRequest 不带 x-amz-acl
      → public/ 的可读性完全由 Bucket Policy 提供（§6）
      → getACL() 仍然计算，但仅用于决定 URL 形态，不发送
```

**一致性要求**：`getACL()` 在两种模式下都必须返回相同结果，
否则同一 fileKey 在 local 判定与 cloud 判定下会得到不同 URL 形态。
因此 `getACL()` 的修正（`contains` → 严格前缀）必须**先于** `send-object-acl` 引入，
顺序落在 F0-0 与 F0-1（§15）。

### 4.4 public-url-prefix 必须显式配置（否则 public 资产不可用）

现状与后果（评审报告 §2.2 已记录）：

```text
FileStorageCloudServiceImpl.java:129   url = cloudPublicUrlPrefix + fileKey
FileStorageCloudServiceImpl.java:154   return ResponseDTO.ok(cloudPublicUrlPrefix + fileKey)
```

`public-url-prefix` 为空时，上述两处返回的是**裸 fileKey**（不是 URL）。
因此采用方案 A 后，**MinIO 环境变量配置 必须显式设置 public-url-prefix**：

```text
public-url-prefix = {endpoint}/{bucket}/         ← 注意必须是 path-style 形态
例如：http://127.0.0.1:9000/xsy-scm-dev/
```

这与 D2 的 path-style 决定一致：**SDK 寻址风格与公开 URL 形态必须同为 path-style**，
否则对象实际路径与公开 URL 拼接结果不一致。

**建议的 fail-fast**（F0-2 可选）：应用启动时若 `mode=cloud` 且
`public-url-prefix` 为空，记录明确警告（不阻断启动），避免出现"上传成功但 public 图不显示"的静默故障。

---

## 5. MinIO Path-Style 寻址（D2）

### 5.1 现状

```text
FileConfig.java:79-82    S3Client      .pathStyleAccessEnabled(false)  ← 硬编码
FileConfig.java:99-102   S3Presigner   .pathStyleAccessEnabled(false)  ← 硬编码
```

MinIO 以 `http://127.0.0.1:9000` 暴露时，虚拟主机风格会把 bucket 当作子域，
解析为 `http://xsy-scm-dev.127.0.0.1:9000/...` → **必然失败**。
这是 F0 的真实阻塞项（评审报告 §2.1）。

### 5.2 设计

```java
// FileConfig 新增
@Value("${file.storage.cloud.path-style-access-enabled:false}")
private Boolean cloudPathStyleAccessEnabled;

@Value("${file.storage.cloud.send-object-acl:true}")
private Boolean cloudSendObjectAcl;
```

```java
// initS3Client() 与 initS3Presigner() 必须使用同一取值（D2 明确要求）
.serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(Boolean.TRUE.equals(cloudPathStyleAccessEnabled))
        .chunkedEncodingEnabled(false)
        .build())
```

要点：

```text
1  默认 false → 上游行为完全不变（非 MinIO 环境零影响）
2  S3Client 与 S3Presigner 共用同一属性，禁止两处各自硬编码
3  chunkedEncodingEnabled(false) 保持不变
4  该修改属"MinIO 兼容需要"，符合 D1 的修改授权口径
```

---

## 6. Bucket Policy（方案 A 的服务端事实源）

### 6.1 策略内容

对 `xsy-scm-dev` 施加**仅针对 `public/` 前缀**的匿名读策略：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadForPublicPrefixOnly",
      "Effect": "Allow",
      "Principal": { "AWS": ["*"] },
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::xsy-scm-dev/public/*"]
    }
  ]
}
```

### 6.2 边界与验证要求

```text
1  Resource 只覆盖 public/*；private/* 不在授权范围，默认拒绝
2  不授予 s3:ListBucket（避免匿名枚举对象）
3  不授予 s3:PutObject / s3:DeleteObject 给匿名主体
4  private/* 必须实测"匿名 GET 被拒"（F0-3 集成测试项）
5  public/* 必须实测"匿名 GET 成功"（F0-3 集成测试项）
6  Bucket 级 anonymous 配置与策略必须一致，禁止在桶级开匿名后靠前缀约定兜底
```

### 6.3 与「一个环境一个 Bucket」的关系

```text
xsy-scm-dev    dev     本波次创建
xsy-scm-test   UAT     本波次只留配置位，不创建
xsy-scm-prod   prod    本波次只留配置位，不创建

Bucket 内统一 public/ 与 private/ 两个前缀，不为每个业务域建 Bucket。
```

---

## 7. 配置（Configuration）

### 7.1 环境变量化（D1 / D8）

```yaml
file:
  storage:
    mode: ${XSY_FILE_STORAGE_MODE:local}
    local:
      upload-path: ${XSY_V2_UPLOAD_PATH:../.runtime/upload/}
      url-prefix:
    cloud:
      region: ${XSY_FILE_REGION:us-east-1}
      endpoint: ${XSY_FILE_ENDPOINT:}
      bucket-name: ${XSY_FILE_BUCKET:}
      access-key: ${XSY_FILE_ACCESS_KEY:}
      secret-key: ${XSY_FILE_SECRET_KEY:}
      private-url-expire-seconds: ${XSY_FILE_PRIVATE_URL_EXPIRE:600}
      public-url-prefix: ${XSY_FILE_PUBLIC_URL_PREFIX:}
      path-style-access-enabled: ${XSY_FILE_PATH_STYLE:false}
      send-object-acl: ${XSY_FILE_SEND_OBJECT_ACL:true}
```

```text
约束 1  默认值必须保持上游可运行（mode 默认 local、path-style 默认 false、send-object-acl 默认 true）
约束 2  任何真实凭据禁止写入 Git / application.yaml / docs（沿用 SMARTADMIN_REFERENCE_RULES §4.4 口径）
约束 3  dev 之外的 profile 只提供配置位，不写死 MinIO 地址
```

### 7.2 复用 dev，以环境变量激活 MinIO（HD-4）

不新增 `dev-minio/`、Maven profile 或 resources 规则。现有 dev/test/pre/prod 保留。
dev 默认 local；MinIO 集成运行通过以下环境变量激活：

```text
XSY_FILE_STORAGE_MODE=cloud
XSY_FILE_ENDPOINT=http://127.0.0.1:9000
XSY_FILE_BUCKET=xsy-scm-dev
XSY_FILE_ACCESS_KEY / XSY_FILE_SECRET_KEY=由本机未跟踪的 .env 提供
XSY_FILE_PATH_STYLE=true
XSY_FILE_SEND_OBJECT_ACL=false
XSY_FILE_PUBLIC_URL_PREFIX=http://127.0.0.1:9000/xsy-scm-dev/
XSY_FILE_PRIVATE_URL_EXPIRE=600
```

上述地址仅为本机示例；跨设备访问时 endpoint 与 public prefix 都须使用客户端可达地址。
生产最终 S3-compatible 供应商另行决定；MinIO 仅为 F0 dev/integration target。

### 7.3 凭据不入库

```text
deploy/minio/.env.example     仅限本地开发的示例凭据（pre/prod 禁用；compose 不含隐式 fallback）
deploy/minio/.env             真实凭据，已被 .gitignore 覆盖（.env / .env.*）
```

---

## 8. 文件大小规则（D5）

### 8.1 现状：四处含义并存

| 位置 | 当前值 | 说明 |
| --- | --- | --- |
| `spring.servlet.multipart.max-file-size` | **20MB** | 四个 profile 一致（dev:89、pre:71、prod:70、test:83） |
| `spring.servlet.multipart.max-request-size` | **10MB** | **小于 max-file-size，自相矛盾** |
| `t_config` → `level3_protect_config` → `maxUploadFileSizeMb` | **30** | V3 种子（:118 区块） |
| `Level3ProtectConfigService` 代码默认 | **50**（`:96`） | t_config 有值时被覆盖，不生效 |

实际瓶颈是 multipart 的 **10MB**。

### 8.2 F0 统一口径

```text
spring.servlet.multipart.max-file-size      = 20MB      （四个 profile）
spring.servlet.multipart.max-request-size   = 25MB      （四个 profile）
t_config.level3_protect_config.maxUploadFileSizeMb = 20
Level3ProtectConfigService 代码 fallback             = 20
```

```text
不允许同时保留 10 / 20 / 30 / 50 四套含义。
Tika MIME 检测保持开启（fileDetectFlag 种子值 true，不改）。
大文件走 Presigned Direct Upload，不在 F0 扩 scope。
```

### 8.3 t_config 的改法（data-only migration）

`t_config.config_value` 为 `TEXT NOT NULL`，内容是 JSON 文本。
**不修改 V3**（已冻结），追加 **V17**：

```sql
-- V17__sa_config_file_upload_size.sql
-- 仅对齐文件上传大小口径（D5）：30 → 20。不改结构，不改其他配置项。
UPDATE t_config
SET config_value = (config_value::jsonb || jsonb_build_object('maxUploadFileSizeMb', 20))::text,
    update_time  = CURRENT_TIMESTAMP
WHERE config_key = 'level3_protect_config';
```

```text
选用 jsonb 合并而非字符串 replace：
  · 不依赖 JSON 的缩进/空格格式，格式变化不会导致静默失效
  · 只覆盖 maxUploadFileSizeMb 一个键，其余键（fileDetectFlag 等）原样保留
```

**注意**：`Level3ProtectConfigService.init()` 在 `t_config` 缺失该键时抛
`ExceptionInInitializerError`（`:118-122`），V17 只做 UPDATE 不删除行，不影响该前置条件。

---

## 9. 上游 `sa-base` 修改清单（D1 授权范围内）

实际 HTTP 路径继承 `SupportBaseController` 的 `/support` 前缀：
`/support/file/upload`、`/support/file/getFileUrl`、`/support/file/downLoad`。
下文 `/file/...` 为方法级映射简称。
HD-4：只修改已有 dev/test/pre/prod YAML 的 ENV 占位，不修改任何 pom/resources profile。


### 9.1 `FileConfig.java`

```text
新增 @Value：cloudPathStyleAccessEnabled（默认 false）、cloudSendObjectAcl（默认 true）
initS3Client()：pathStyleAccessEnabled 改为读取配置
initS3Presigner()：同上，且与 S3Client 使用同一属性（D2）
cloud 启动时检查必填配置、public prefix 绝对 HTTP(S) URL 与尾斜线、TTL 1～604800 秒
pre/prod/production 拒绝本地示例凭据（HD-5）；local 不强依赖任何 cloud 配置
```

```text
授权口径：MinIO 兼容需要。不做框架重构，不改变 bean 结构与条件装配。
```

### 9.2 `FileStorageCloudServiceImpl.java`

```text
getACL()：fileKey.contains(FOLDER_PUBLIC) → 严格前缀判定（D4 明确要求）
upload()：.acl(acl) 改为按 cloudSendObjectAcl 条件发送（D3）
非法前缀（既非 public/ 也非 private/）的处理：拒绝，不静默降级为 public
FileKeyPolicy 拒绝路径跳转、编码别名、反斜线、query/fragment 等非规范 key
upload 在 t_file 插入之前直接签名新对象；后续 getFileUrl 仍查询既有元数据
private TTL <= 5 秒不缓存；其他 TTL 缓存 expire-5 秒，key 包含 bucket/endpoint/TTL
delete 清除对应签名缓存；不删除 t_file，不新增原生不存在的删除端点
```

```text
授权口径：MinIO 兼容 + 安全需要。
不改变 upload / getFileUrl / download / delete 的既有方法签名。
```

### 9.3 `FileController.java` + 文件访问安全

```text
getUrl()、downLoad()：接入可见性门禁（§3.3 / §3.4）
upload()：folder 白名单断言（可选）
新增 FileAccessGuard（底座层，供 Controller 复用），对逗号分隔的每一个 key 都鉴权
新增 FileAccessIdentity 接口与 sa-admin 的 AdminFileAccessIdentity 适配
    只桥接 RequestEmployee.administratorFlag 与 StpUtil.hasPermission；不修改认证实现
    避免 sa-base 反向依赖 sa-admin；creator 同时匹配用户类型，防止跨类型 ID 碰撞
新增 FileUploadExceptionHandler，仅映射 multipart 超限为 HTTP 413 + 原生参数错误 envelope
    multipart 解析可能早于 Controller 选择，因此用专项 advice，避免落入上游 Throwable 系统错误
```

```text
授权口径：安全需要（D4）。
明确不做：不重写 FileService、不重建 t_file、不改方法签名、不改返回结构。
```

### 9.4 `Level3ProtectConfigService.java`

```text
maxUploadFileSizeMb 代码 fallback：50 → 20（D5）
fileDetectFlag 默认值保持不变
```

```text
授权口径：配置口径统一（D5）。仅改默认值常量。
```

### 9.5 `AGENTS.md` 仓库结构说明（D8）

实施前 §2 的仓库结构图把以下内容列为既有；F0 已按 HD-8 修正：

```text
xsy-device-agent/    不存在
deploy/minio/        F0 已创建部署文件（是否已启动须以验收报告为准）
docker-compose.yml   不存在（D8 明确不要求创建根目录 compose）
```

F0 修正为与实际一致的口径，并**只在此处描述一次**（D9 去重要求）。

---

## 10. 测试（Tests）

### 10.1 后端单元测试（不依赖 MinIO）

```text
FileAccessGuardTest
    · 前缀严格性：public/ 放行、private/ 进入归属判定、"publicity/" 与 "privatex/" 被拒
    · 逐 folder 策略：common 严格 / notice 豁免 / help-doc 豁免 / feedback 严格
    · 归属判定：administratorFlag、support:file:query、creator_id 本人、三者皆无
    · 空 fileKey / null fileKey / 大小写变形（PUBLIC/、Public/）拒绝

FileConfigTest
    · path-style-access-enabled 默认 false
    · send-object-acl 默认 true
    · S3Client 与 S3Presigner 取到同一 path-style 取值

FileStorageCloudServiceImplTest（可用 mock S3Client）
    · getACL 对 private/common/ 返回 PRIVATE（原 contains 语义在边界上会误判）
    · send-object-acl=false 时 PutObjectRequest 不携带 acl
```

### 10.2 云端集成测试（**必须使用 cloud/MinIO 环境变量配置**）

```text
F0FileStorageCloudIT
    · 上传 public/ 对象 → 返回 public-url-prefix 拼接的 URL → 匿名 GET 成功
    · 上传 private/ 对象 → 返回 Presigned URL → 匿名 GET 被拒 → Presigned GET 成功
    · Presigned URL 过期：以短 TTL（如 2s）验证过期后拒绝
    · download 端点返回字节流与元数据
    · IFileStorageService.delete 删除对象后确认 t_file 记录仍保留（原生无删除 HTTP 端点；不扩增接口）
    · 元数据落库：t_file 的 file_key / folder_type / file_size / creator_* 正确
    · MIME 拒绝：伪装扩展名的非白名单内容被 Tika 拒绝
    · 超限拒绝：> 20MB 文件被拒绝
    · 非法前缀：构造非 public/ 非 private/ 的 fileKey，读取被拒
```

### 10.3 回归（沿用既有基线，不新增）

```text
后端：mvn -B -pl sa-admin -am test            基线 317/0/0/0
后端：mvn -B -pl sa-admin -am test -Dtest='*IT'  基线 182/0/0/0
前端：node --experimental-strip-types --test test/*.test.mjs   基线 50/50
前端：npm run lint（0 error）、TS 棘轮、npm run build
```

### 10.4 测试不得做的事

```text
不得用 mock 替代 §10.2 的 MinIO 真实往返（F0 的核心风险就在真实 S3 语义）
不得把 local 模式的通过结果当作 Presigned URL 验收证据
不得为提升覆盖率给 DTO / getter 写测试
```

---

## 11. Playwright

### 11.1 新增 `xsy-scm-web/e2e/f0-file-storage.spec.ts`

```text
场景 1  文件管理页面上传 → 列表出现新记录（t_file 落库可见）
场景 2  上传图片后预览 URL 可加载（public 或 presigned，取决于 folder）
场景 3  private 附件在详情页可正常下载（保证原生 Notice/HelpDoc 未被门禁破坏）
场景 4  越权读取被拒：以无权限上下文请求他人 private/common/ 的 fileKey → 403
场景 5  非法前缀被拒：构造非 public/ 非 private/ 的 fileKey → 403
场景 6  超限文件被拒：> 20MB → 明确的参数错误
场景 7  非法 MIME 被拒：伪装扩展名 → 明确的参数错误
```

### 11.2 必须在 cloud/MinIO 环境变量配置 下执行

```text
场景 2 / 3 的 URL 语义与 local 模式不同：
    local  → urlPrefix + fileKey（永不过期）
    cloud  → public-url-prefix 或 Presigned URL（会过期）
因此 F0 验收只接受 cloud/MinIO 环境变量配置 下的结果。
```

### 11.3 回归

```text
e2e/smartadmin-native.spec.ts          基线 17/17（含文件管理页面冒烟）
e2e/scm-*.spec.ts（W1–W5）             基线 23/23
```

---

## 12. Rollback

```text
触发条件
    cloud 模式导致原生文件流程（Notice / HelpDoc / Feedback / OA 企业）不可用
    或 MinIO 不可达导致上传/下载整体失败

回滚动作（按顺序，取最小即可）
    1  将 XSY_FILE_STORAGE_MODE 改回 local（无需改代码、无需改迁移）
    2  如需彻底回退代码：还原 FileConfig / FileStorageCloudServiceImpl /
       FileController / FileService / Level3ProtectConfigService 与新增门禁/身份桥接类
       （不含数据结构变更；回退门禁将重新暴露已关闭的缺口，不应作为默认回滚动作）

不需要回滚的内容
    V17 只把 maxUploadFileSizeMb 由 30 改为 20，属口径对齐，保留无害
    deploy/minio/compose.yaml 为纯新增，保留无害
    t_file 结构未变，历史记录不受影响

数据影响
    F0 不迁移任何历史文件；local 模式下已上传的对象仍可被 local 模式读取
    cloud 模式下新上传对象落在 MinIO；切换回 local 后这些对象不可见
    → 因此 F0-3 之后如需回退，必须确认没有仅存在于 MinIO 的业务引用
```

---

## 13. DoD（完成定义）

### 13.1 硬性通过项

```text
[ ] FileService 未被重写；t_file 未重建；文件管理页面未重做
[ ] V1–V16 逐字节未变（哈希校验）；仅追加 V17
[ ] reference 目录零修改

[ ] MinIO 上传 PASS
[ ] MinIO 下载 PASS
[ ] MinIO 删除 PASS
[ ] public/* 匿名 GET PASS（Bucket Policy 生效）
[ ] private/* 匿名 GET 被拒 PASS
[ ] private/* Presigned URL PASS
[ ] Presigned URL 过期后拒绝 PASS（配置生效）
[ ] 元数据写入 t_file PASS

[ ] MIME 校验 PASS（Tika 保持开启）
[ ] 超限拒绝 PASS（20MB）
[ ] 非法前缀拒绝 PASS
[ ] 越权读取他人 private/common/ 被拒 PASS
[ ] 原生 Notice / HelpDoc / Feedback / OA 企业 附件读取未被破坏 PASS

[ ] 文件大小口径唯一：multipart 20/25、t_config 20、code fallback 20，无 10/30/50 残留
[ ] path-style-access-enabled 默认 false；MinIO 环境变量配置 为 true；S3Client 与 S3Presigner 取值一致
[ ] send-object-acl 默认 true；MinIO 环境变量配置 为 false；上传不因 canned ACL 失败

[ ] PostgreSQL 回归 PASS
[ ] SmartAdmin native smoke PASS（17/17）
[ ] W1–W5 Playwright 回归 PASS（23/23）
[ ] 前端 lint / typecheck 棘轮 / unit / build PASS
[ ] AGENTS 仓库结构说明与实际一致
```

### 13.2 明确不计入 DoD

```text
local 模式下的 Presigned URL 验收（local 无可见性语义，D9/§0.3 已豁免）
SCM 业务附件表（Non-scope）
SCM 业务权限与文件归属关系（F1）
Presigned Direct Upload（后续）
orphan cleaner / TTL 清理（后续）
```

### 13.3 必须报告为"未执行"的情形

```text
任何因 MinIO 不可达而未执行的集成测试
任何在 local 模式下代替 cloud 执行的验收项
```

---

## 14. 实施步骤（对应 §0.3 的固定顺序）

```text
F0-0  Security Closure
      - getACL 的 contains → 严格前缀判定
      - 新增 FileAccessGuard（前缀 + 可见性 + 逐 folder 策略）
      - FileController.getUrl / downLoad 接入门禁
      - 单元测试（FileAccessGuardTest / FileStorageCloudServiceImplTest）
      - 门槛：原生 Notice/HelpDoc/Feedback/OA 读取回归通过

F0-1  MinIO Compatibility
      - FileConfig 增加 path-style-access-enabled 与 send-object-acl
      - S3Client / S3Presigner 共用 path-style 取值
      - FileStorageCloudServiceImpl 按 send-object-acl 条件发送 acl
      - 单元测试（FileConfigTest）

F0-2  Configuration Alignment
      - 四个 profile 的 multipart 20/25
      - Level3ProtectConfigService fallback 50 → 20
      - V17 data-only 对齐 t_config 的 20
      - cloud 参数环境变量化；复用 dev，不新增 Maven/resources profile
      - （可选）cloud 模式下 public-url-prefix 为空的启动警告

F0-3  Cloud Activation + Real Integration Test
      - deploy/minio/compose.yaml + .env.example（bucket + public/* policy bootstrap）
      - MinIO 环境变量配置 激活 mode: cloud
      - F0FileStorageCloudIT 全部通过（§10.2）
      - F0 Playwright 场景 1–7（§11.1）

F0-4  Full Regression
      - 后端单测 / PG IT / 前端 lint / 棘轮 / unit / build
      - smartadmin-native 17 项 + W1–W5 23 项
      - V1–V16 哈希一致、reference 零修改
      - AGENTS 仓库结构说明同步修正
```

---

## 15. 规则落点（D9）

```text
AGENTS.md
    只增加不重复的全局硬规则，例如：
      - 所有二进制必须经 SmartAdmin IFileStorageService
      - 业务模块不得直接写本地磁盘
      - PostgreSQL 只存元数据与引用，不存二进制
      - 禁止 SCM 建立第二套上传设施
      - 生产存储必须是 S3-Compatible 对象存储
    并先去重：AGENTS.md §8 已有 "store large images directly in relational tables"（Do not 列表）
    与 "Use object storage for images and large attachments."，不得重复表达。

本 Target Design（唯一详细落点）
    MinIO 配置、path style、object key 格式、bucket policy、
    逐 folder 可见性策略、attachment lifecycle、Presigned 演进

SMARTADMIN_REFERENCE_RULES.md
    不重复写入上述内容

明确不做
    不把同一套规则同时写进 AGENTS.md、SMARTADMIN_REFERENCE_RULES.md 与 design docs 三处
```

---

## 16. 人类决策（Human decisions，2026-09-17 已确认）

本次 HD-1～HD-9 已全部裁决；记录后直接按 F0-0 → F0-4 实施，无需再次确认。
完整授权见 [F0 批准记录](./2026-09-17-f0-object-storage-approval.md)。

| 裁决 | 状态 | 最终决定 |
| --- | --- | --- |
| HD-1 | APPROVED | `private/notice/`、`private/help-doc/` 登录即可读，豁免 creator/ownership；是 authenticated internal resource，绝非 anonymous public。 |
| HD-2 | APPROVED | 仅新增 V17 data-only migration，将 `t_config.level3_protect_config.maxUploadFileSizeMb` 对齐为 20；V1–V16 禁止修改。 |
| HD-3 | APPROVED WITH FOLLOW-UP DEBT | `private/common/` 自 F0 起严格执行 administrator OR `support:file:query` OR creator 为当前员工，否则 HTTP 403。正式引入任何非管理员业务角色前，OA 企业证照等 COMMON 私密资产必须迁到业务 permission + ownership/relation + FileService；禁止为兼容页面再次放宽 COMMON。 |
| HD-4 | OVERRIDE RECOMMENDATION | 不新增 dev-minio Maven/resources profile；复用 dev，local 默认不变，cloud/MinIO 全部通过 ENV 激活，避免扩大 `${profiles.active}` 的 dev/test/pre/prod 资源规则。 |
| HD-5 | APPROVED WITH SAFETY RULE | `.env.example` 可给仅限本地开发的示例凭据；compose 不含隐式生产 fallback；真实 `.env` 必须 gitignore；pre/prod 禁止复用示例凭据。 |
| HD-6 | APPROVED WITH VERSION POLICY | 禁止 latest、旧 server `RELEASE.2025-09-07T16-13-09Z` 与第三方 MinIO canonical 镜像。官方源码 tag `RELEASE.2025-10-15T17-29-55Z` 构建为 `xsy-scm/minio:RELEASE.2025-10-15T17-29-55Z`；mc 固定 `quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z`（2026-09-17 验收修正：Docker Hub 该仓库已 404，改官方 quay.io，tag 与摘要不变）。允许 deploy/minio 下新增构建文件。MinIO 仅为 dev/integration S3-compatible target，不承诺生产选型。 |
| HD-7 | APPROVED AS ENV-DRIVEN | `XSY_FILE_PUBLIC_URL_PREFIX` 可覆盖，禁止永久写死 127.0.0.1；本机示例为 `http://127.0.0.1:9000/xsy-scm-dev/`，手机/其他电脑改用可达 LAN IP、hostname 或 domain。本机 Playwright 可用 127.0.0.1。 |
| HD-8 | APPROVED | AGENTS 描述真实 `deploy/minio/`，明确当前不存在根 docker-compose.yml、xsy-device-agent/。 |
| HD-9 | APPROVED | 不给非管理员预置 `support:file:query`；无正式非管理员种子角色，授权留正式 RBAC/业务角色设计。 |

### 强制后续债务 F0-DEBT-01

在正式引入任何非管理员业务角色之前，必须验收 OA 企业证照等 COMMON 私密资产的
业务 permission + ownership/relation + FileService 读取链路。该事项是角色上线前置门槛，
不能通过放宽 `/file/getFileUrl` 或 `/file/downLoad` 的 COMMON 门禁消除。
F0 只验证管理员、文件查询权限持有者、上传者的允许路径与其他员工的 403；
并不宣称所有未来 OA 读者已拥有正确业务关系授权。

---

## 17. 边界声明

- 本文件是 F0 的 **Target Design**，不是实施结果。HD-1～HD-9 已确认，实施已获授权。
- 本文件定义已批准实施契约；按 F0-0 → F0-4 直接执行，验收结果另见验收报告。
- 本文件不改变 W6 的状态：W6 仍未启动，且 F0 验收完成前不得开始。
- 本文件不构成对 F1–F4（Product Media / Supplier-Customer Certificate / Traceability /
  Purchase-Finance Attachment）的范围承诺；D6 已锁定 `product_image` 为商品图片正式模型。
- 本文件与外部设计《XSY-SCM 文件存储与对象存储激活设计》的差异，以本文件为准；
  差异集中在：MinIO path-style 为代码改动而非纯配置（§5）、
  public 资产需显式 `public-url-prefix` 与 Bucket Policy（§4.4 / §6）、
  canned ACL 需可关闭（§4.3）、上传安全需逐 folder 策略而非统一权限（§3.4）、
  文件大小需三处同时对齐（§8）。
