# 文件存储与附件体系设计 评审报告

日期：2026-09-17（第二轮，基于提交 `663d354`）。评审对象为两份外部设计文档：

```text
v1  《XSY-SCM 文件存储与附件体系设计》          —— 首版，统一文件体系与业务附件建模
v2  《XSY-SCM 文件存储与对象存储激活设计》      —— 二版，聚焦 F0 对象存储激活
```

评审方式：逐条对照当前工作区真实代码与已冻结迁移。本报告只做核对，不修改任何代码。

结论：**v2 已吸收首轮评审的多项意见，方向正确；但"MinIO 只是配置问题"这一核心论断不成立，
且 §21 的配置与其 §32 验收条件自相矛盾。F0 在修正以下阻塞项之前无法通过验收。**

本报告不构成 F0 的批准，也不改变阶段边界（W5.5 已完成，W6 未启动）。

---

## 0. 本轮变更摘要

### 0.1 仓库更新（`663d354` W5.5 SmartAdmin 原生功能一致性同步）

该提交对文件域的影响为**零**：

| 项 | 结果 |
| --- | --- |
| 新增迁移 | **0**（V1–V16 冻结，16/16 哈希一致） |
| 菜单 / 权限改动 | **0** |
| `sa-base/**/module/support/file`（14 文件） | **逐字节一致**，未改动 |
| 唯一相关改动 | `dev/sa-base.yaml` 的 `upload-path` 去掉 `D:` 盘符硬编码 → `../.runtime/upload/`（:121） |

因此首轮评审的全部结论**在新提交上依然成立**。

### 0.2 v2 已吸收的首轮意见

| 首轮评审发现 | v2 处置 |
| --- | --- |
| §20/§25.3 低估既有上传安全能力 | **已修正**：v2 §3 明确"已用 Apache Tika 检测真实 MIME""不重新实现 MIME 检测" |
| 未反映 W5.5 的 upload-path 变更 | **已修正**：v2 §4 正确引用 `../.runtime/upload/` 与 `XSY_V2_UPLOAD_PATH` |
| §16 MinIO 的 `public-url-prefix` 与上游前提冲突 | **部分修正**：v2 §21 改为 `${XSY_FILE_PUBLIC_URL_PREFIX:}` 空默认值，与上游注释一致（但见 §2.2） |
| §24 `contains` → `startsWith` | **保留**，与首轮一致 |
| §8 `product_media` 与已冻结 `product_image` 冲突 | **部分缓解**：v2 §30 把"新增商品图片表"列入 F0 禁止项，F0 范围内不再冲突（但 F1 仍未解决，见 §3.1） |
| §29 规则与 AGENTS.md §8 重复 | **未处置**（见 §3.3） |
| `FileController` 无权限门禁 | **未处置**（见 §2.4） |
| local 模式无 public/private 语义 | **未处置**（见 §2.5） |
| SCM 文件类别与 `FileFolderTypeEnum` 的桥接 | **未处置**（见 §3.2） |
| `deploy/`、`docker-compose.yml` 实际不存在 | **未处置**（见 §3.4） |

### 0.3 v2 新增的有效发现

v2 §23 指出 multipart 配置自相矛盾。经核对**属实**，详见 §2.3 —— 但 v2 只抓到了三处大小限制中的一处。

---

## 1. 核对通过项（v2 断言成立）

| v2 断言 | 核对结果 | 证据 |
| --- | --- | --- |
| 文件管理基础设施完整（Controller→Service→IFileStorageService→Local/Cloud） | 一致 | `sa-base/.../support/file/**`（14 文件） |
| `t_file` 字段清单 | 逐字段一致 | `V5__sa_system_remaining_tables.sql:150-164` |
| `FileService` 职责 7 步 | 一致 | `FileService.java:68-117` |
| 已用 Apache Tika 检测真实 MIME | 一致 | `SecurityFileService.java:74-90` |
| 允许类型不含 SVG | 一致 | `SecurityFileService.java:40-42`（`image/jpeg,png,gif,bmp`，注释明确排除 SVG） |
| 当前 `mode: local`，默认目录 `../.runtime/upload/` | 一致 | `dev/sa-base.yaml:118,121` |
| §24 `fileKey.contains("public")` 需改 `startsWith("public/")` | **准确** | `FileStorageCloudServiceImpl.java:227` 用 `contains`，`:152` 用 `startsWith` |
| §23 multipart `max-file-size: 20MB` / `max-request-size: 10MB` 矛盾 | **属实** | 四个 profile 均如此（见 §2.3） |

补充事实：`IFileStorageService`、`FileController`、`FileConfig`、`FileFolderTypeEnum`、
`SecurityFileService` **全部位于 `sa-base`（上游框架层）**。v2 §31 的 T0–T7 除 T1（MinIO 容器）
外，几乎每一步都会落到上游层，需遵守 [SMARTADMIN_REFERENCE_RULES](../../SMARTADMIN_REFERENCE_RULES.md) §7.1。

---

## 2. v2 需要修正的判断

### 2.1 §21「MinIO 主要是配置问题」不成立 —— 这是代码阻塞项（最高优先级）

v2 §21 开头写"当前 `FileStorageCloudServiceImpl` 已是 S3 Compatible 方式，因此 MinIO 主要是配置问题"，
§36 进一步断言"缺的是把已存在的文件中心从 Local 激活成对象存储"。**该论断错误。**

`FileConfig` 两处 S3 客户端均硬编码**虚拟主机风格寻址**：

```text
FileConfig.java:79-82    S3Configuration.builder()
                             .pathStyleAccessEnabled(false)      ← 硬编码
                             .chunkedEncodingEnabled(false)
FileConfig.java:99-102   同上（S3Presigner）
```

MinIO 以 `http://127.0.0.1:9000` 暴露时，虚拟主机风格会把 bucket 当子域解析为
`http://xsy-scm-dev.127.0.0.1:9000/...`，**必然失败**。必须启用 path-style 寻址，
而这是 `sa-base` 上游代码改动，**不是 v2 §21 那份 yaml 能解决的问题**。

**后果**：按 v2 §31 执行到 T3「Activate」时会直接卡住，T5/T6 的 public/private 真实验证无法开始。
F0 必须新增一步"修正 `FileConfig` 寻址风格"，并说明如何以最小侵入方式处理上游基线。

### 2.2 §21 的空 `public-url-prefix` 与 §32 DoD「Public URL PASS」自相矛盾

v2 §21 给出：

```yaml
public-url-prefix: ${XSY_FILE_PUBLIC_URL_PREFIX:}      # 默认空
```

空值本身是对的（与上游 `dev/sa-base.yaml:130` 注释"minio默认是不支持的……可以配置为空"一致），
但**空值下 Public 文件不会返回 URL**：

```text
FileStorageCloudServiceImpl.java:129    String url = cloudConfig.getCloudPublicUrlPrefix() + fileKey;
                                        → 空前缀时 url == fileKey（裸对象键，不是 URL）
FileStorageCloudServiceImpl.java:154    return ResponseDTO.ok(cloudConfig.getCloudPublicUrlPrefix() + fileKey);
                                        → 同上
```

即 `upload` 与 `getFileUrl` 在 public 路径下都会返回**裸 fileKey**。
前端 `product-image-upload.vue:10` 是 `<a-image :src="image.fileUrl" />`，拿到裸键必然显示失败。

同时 MinIO 默认不支持 `ObjectCannedACL.PUBLIC_READ`，`getACL()`（`:225-232`）返回的
`PUBLIC_READ` 在 MinIO 上还需 bucket policy 配合才生效。

**结论**：v2 §32 的「Public URL PASS」在当前配置下不可能通过。需要二选一并写入设计：

```text
方案 A：为 MinIO 配置可公开访问的 bucket policy + 显式 public-url-prefix
方案 B：放弃 MinIO 下的公开直链，public 资产同样走 presigned URL
        （此时"public/private"仅是 Key 命名约定，不再决定访问方式）
```

### 2.3 §23 抓到了 multipart 矛盾，但漏掉另外两处大小限制

**§23 的发现属实**（四个 profile 全部如此）：

```text
dev/sa-base.yaml:89-90    max-file-size: 20MB  /  max-request-size: 10MB
pre/sa-base.yaml:71-72    同上
prod/sa-base.yaml:70-71   同上
test/sa-base.yaml:83-84   同上
```

单文件上限 20MB 却把整请求限制在 10MB，确实自相矛盾。

但上传大小实际上由**三处**共同决定，v2 只提到一处：

| 位置 | 取值 | 是否生效 |
| --- | --- | --- |
| `spring.servlet.multipart` | `max-file-size: 20MB` / `max-request-size: 10MB` | 生效，且 10MB 是**实际瓶颈** |
| `t_config` → `level3_protect_config` → `maxUploadFileSizeMb` | **30** | 生效，被 multipart 的 20MB 压制 |
| `Level3ProtectConfigService` 代码默认 | 50（`:96`） | 不生效（t_config 有值） |

v2 §23 建议改成 `20MB / 25MB`，但**未处理 `t_config` 里的 30MB**，三者仍不一致。
另需说明：`fileDetectFlag` 在 V2 种子中为 **true**（MIME 白名单**已在强制执行**），
v2 §3 虽已提到 Tika，但未点明它已开启，容易让人以为只是"代码存在"。

**结论**：§23 应扩展为"统一三处大小口径"，并明确 `t_config` 是否随 F0 一并调整。

### 2.4 §25 低估了 `FileController` 的权限缺口

v2 §25 写"SmartAdmin File API 只解决文件存储，SCM 必须额外解决业务权限"，
把问题定性为 **SCM 侧需要补充业务权限**。实际缺口更靠前：

```text
FileController.java:44-72
  POST /file/upload       无 @SaCheckPermission
  GET  /file/getFileUrl   无 @SaCheckPermission
  GET  /file/downLoad     无 @SaCheckPermission
grep 计数：SaCheckPermission = 0
```

即**整个文件接口当前没有任何权限门禁**。任何持有 fileKey 的已登录用户，
都可通过 `/file/getFileUrl` 或 `/file/downLoad` 读取 `private/` 下的对象 ——
不需要任何 SCM 业务权限，也不需要猜路径（fileKey 会随业务接口返回）。

v2 §25 的目标句"不要形成'知道 fileKey 就可以下载私密文件'的业务接口"**恰恰描述了当前的既有状态**。

**结论**：§25 应从"SCM 需补业务权限"升级为两段式：

```text
第一段（F0 必做）：为 FileController 三端点补权限门禁，堵住 fileKey 直读
第二段（F1+）：SCM 各域按 scm:<domain>:query 做业务权限 + 关系校验
```

这是本次评审中**唯一一个已存在的安全缺口**，建议独立于 F0 业务功能先行修复。

### 2.5 §22 保留 local 默认与 §32 的 Presigned 验收条件不兼容

`FileStorageLocalServiceImpl` 无任何可见性区分：

```text
FileStorageLocalServiceImpl.java:133-141   getFileUrl → urlPrefix + fileKey（纯拼接）
FileStorageLocalServiceImpl.java:71-115    upload 直接 transferTo 本地目录
FileConfig.java:118-124                    addResourceHandlers 把 /upload/** 静态映射到 upload-path
```

local 模式下 `private/` 下的文件同样可被公开访问。

v2 §22 保留 `mode: local` 作为默认（合理），但 §32 DoD 同时要求
"Private Presigned URL PASS / Private URL expiry configured"。**这两项在 local 模式下不可能通过。**

**结论**：§32 需注明"Presigned 相关项仅在 cloud 模式下适用"，或让 F0 验收在 cloud profile 下执行。

---

## 3. v2 仍未解决的问题（承接首轮）

### 3.1 §16 `product_media` 与已冻结 `product_image` 的关系仍未定义

v2 §30 把"新增商品图片表"列为 F0 禁止项，F0 范围内不再冲突 —— 这一步是对的。
但 §16 仍以"后续商品图片建议新增 `product_media`"的形式提出，**未提及 `product_image` 已存在**：

```text
V6__scm_product.sql:82-100
  product_image(id, spu_id, file_key, file_url, file_name, file_size,
                is_primary, sort_order, version, deleted, 审计字段)
  uk_product_image_primary_active ON product_image(spu_id) WHERE deleted = FALSE AND is_primary = TRUE
  -- 每个 SPU 至多一张主图，与 v2 §16"每个 Product 最多一个主封面"规则一致
```

V6 已应用且冻结，不可修改。同时 v2 §14 禁止库内存 URL，而 `product_image.file_url` 是 `NOT NULL`。

**待决策**：`product_media` 是与 `product_image` 并存，还是新增迁移 + 数据搬迁后废弃？
F1 开工前必须给出结论与迁移口径。

附带说明（首轮已发现，v2 未涉及）：`ProductQueryService.enrich():58-59,74` 已在**读取时按 fileKey 重解析 URL**
并覆盖库中存量值，因此云模式下不会向界面泄漏过期 URL —— 该点实际上比 v2 §14 的担忧更乐观；
但 `:74` 在 fileKey 缺失时会把 `fileUrl` 静默置 `null`，导致图片无声消失。

### 3.2 §13 `ScmFileKeyBuilder` 与 `FileFolderTypeEnum` 的桥接未定义

`FileService.fileUpload` 强制 `folder` 参数取 `FileFolderTypeEnum`：

```text
FileService.java:68-72   SmartEnumUtil.getEnumByValue(folderType, FileFolderTypeEnum.class)
                         null → ResponseDTO.userErrorParam("文件夹错误")
FileService.java:95      fileStorageService.upload(file, folderTypeEnum.getFolder())
```

而该枚举在 `sa-base` 上游，且现有 4 个值**全部位于 `private/` 下**：

```text
FileFolderTypeEnum.java:23-38
  COMMON(1,   "private/common/")     NOTICE(2,    "private/notice/")
  HELP_DOC(3, "private/help-doc/")   FEEDBACK(4,  "private/feedback/")
  —— 当前不存在任何 public/ 类型的 folder
```

v2 §11/§13 的 `{visibility}/{domain}/{businessId}/{category}` 方案与
`t_file.folder_type`（`SMALLINT NOT NULL`，取值域绑定该枚举）之间**没有映射定义**。只有两条路：

```text
路径 A：向 sa-base 的 FileFolderTypeEnum 追加 SCM 业务类别（改上游枚举）
路径 B：SCM 直连 IFileStorageService 并自行写 t_file
        （等于复制 FileService 的落库与安全校验，违反 v2 §0"禁止重新实现第二套文件上传中心"）
```

**结论**：v2 §13 必须明确选择 A 或 B，并给出 `folder_type` 取值映射。

### 3.3 §33 的 18 条规则与 AGENTS.md 存在重复

至少 3 条已存在：

```text
AGENTS.md §8  →  "store large images directly in relational tables"（已在 Do not 列表）
AGENTS.md §8  →  "Use object storage for images and large attachments."
```

新增前需去重，并明确归属 `AGENTS.md`（工程规则）还是
[SMARTADMIN_REFERENCE_RULES](../../SMARTADMIN_REFERENCE_RULES.md)（底座规则）。

### 3.4 MinIO 基础设施与 `deploy/` 实际不存在

v2 §31 T1 要求"新增开发用 MinIO（9000 API / 9001 Console / Bucket `xsy-scm-dev`）"。
当前仓库**没有 `deploy/` 目录，也没有 `docker-compose.yml`**：

```text
缺失: deploy
缺失: docker-compose.yml
```

而 [AGENTS.md](../../AGENTS.md) §2 的仓库结构图把二者列为既有内容。F0 T1 需从零创建，
并建议同步修正 AGENTS.md 的结构声明。

### 3.5 AGENTS.md 尚未同步 W5.5，F0 存在治理缺口

`663d354` 的提交信息写"W5.5 完成。停止。不进入 W6 Inventory"，
且 v2 §35 的优先级图把 W5.5 标为已完成。但 AGENTS.md 状态段仍停留在：

```text
AGENTS.md:62   ### Current delivery status (2026-09-16)
AGENTS.md:71   W6  Inventory / Mini Program         NOT STARTED
AGENTS.md:77   The current task stops after W5 acceptance. Do not start W6 or add unrelated business scope.
```

**AGENTS.md 中没有 W5.5**。后果是：任何按 AGENTS.md 行事的 agent 读到 §77 都会拒绝启动 F0
（无论 F0 是否算"unrelated business scope"）。F0 开工前应先把 AGENTS.md 的状态段同步到 W5.5，
并明确 F0 是否为独立批准的波次。

---

## 4. 修正版 F0 实施顺序

在 v2 §31 的 T0–T7 基础上，插入三个必要步骤，并把安全修复独立前置：

```text
F0-0  安全缺口修复（建议独立于 F0 先行，最小改动）
      - FileController 三端点补权限门禁            ← §2.4
      - getACL() 的 contains → startsWith           ← §1（v2 §24 已要求）
      - ProductQueryService.enrich():74 的 fileUrl 静默置 null 补告警

F0-1  MinIO 可用性（v2 缺失的阻塞步骤）
      - FileConfig 支持 path-style 寻址            ← §2.1，新增配置项，默认保持上游 false 行为
      - 明确 MinIO 下 public 资产的访问方式         ← §2.2，方案 A 或 B 二选一

F0-2  配置口径统一（v2 T2 + T4 扩展）
      - multipart max-file-size / max-request-size  ← §2.3
      - t_config 的 maxUploadFileSizeMb 是否同步调整 ← §2.3
      - 全部 cloud 参数环境变量化（v2 §21 已给出）

F0-3  激活与验证（v2 T3 + T5 + T6）
      - cloud 模式激活；保留 local 默认 + 独立 minio profile（v2 §31 T3 建议合理）
      - public / private / presigned 真实验证 —— 须在 cloud 模式下执行   ← §2.5
      - upload / download / delete / mime reject / oversize reject

F0-4  回归与收口（v2 T7）
      - SmartAdmin native smoke 17 项、W1–W5 Playwright 23 项
      - V1–V16 哈希一致、reference 零修改
```

---

## 5. 待决策事项

| # | 事项 | 影响 | 出处 |
| --- | --- | --- | --- |
| 1 | 是否批准修改 `sa-base` 上游：`FileConfig`（path-style）、`FileController`（权限）、`FileFolderTypeEnum`（枚举） | F0 能否开工 | §2.1 / §2.4 / §3.2 |
| 2 | MinIO 下 public 资产走公开直链还是 presigned（方案 A / B） | §32 DoD 能否达成 | §2.2 |
| 3 | 上传大小三处口径如何统一，`t_config` 是否随 F0 调整 | 验收基线 | §2.3 |
| 4 | `product_media` 与已冻结 `product_image` 的关系（并存 / 迁移替代） | F1 开工前提 | §3.1 |
| 5 | SCM 文件类别走路径 A（扩上游枚举）还是路径 B（SCM 直连） | F1+ 全部业务附件设计 | §3.2 |
| 6 | F0 是否作为独立批准波次，AGENTS.md 状态段如何同步 W5.5 | 治理合规 | §3.5 |
| 7 | `deploy/` 与 `docker-compose.yml` 由 F0 创建还是另行补齐 | F0 T1 | §3.4 |
| 8 | v2 §33 的 18 条规则归属哪个文件、如何与 AGENTS.md §8 去重 | 规则一致性 | §3.3 |

---

## 6. 边界声明

- 本次评审**未修改任何代码、迁移或配置**；仅更新本报告。
- 本报告同时覆盖 v1 与 v2 两份设计文档，作为该主题的**唯一现行评审入口**（遵循文档维护"同一主题只维护一个现行入口"）。
- 本报告不改变阶段进度：W0–W5 与 W5.5 仍为 COMPLETE，W6 未启动。
- v2 的 F1–F4（Product Media / Supplier-Customer Certificate / Traceability / Purchase-Finance Attachment）
  涉及未启动业务域，本次仅核对与现状的结构兼容性，未评估排期。
- 本报告不构成 F0 的批准文件；F0 若实施，仍需按波次流程出具 target-design 与 approval。
