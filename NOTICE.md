# 第三方组件与许可说明

## SmartAdmin（net.lab1024）

本项目的后端基础设施与前端管理后台以 SmartAdmin 为底座构建。
上游以 **MIT 许可证**发布，其版权与许可声明完整保留于仓库根目录的 `LICENSE` 文件。

## 关于源码注释头

为统一产品标识，2026-09-18 已从各源码文件的注释头中移除上游署名与联系方式
（`@Author` / `@Date` / `@Wechat` / `@Email` / `@Copyright` 等行），
并清理了 Swagger 描述与少量界面文案中的上游字样。

**MIT 要求保留的版权与许可声明并未随文件头一并删除** ——
它们完整保留在本文件与 `LICENSE` 中，这是 MIT 条款
（"The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software."）的合规方式。

## 因结构性/功能性而保留的标识

以下内容不属于「说明性文字」，改动会破坏编译或运行，因此有意保留：

| 标识 | 位置 | 保留原因 |
|---|---|---|
| Java 包名 `net.lab1024.sa.*` | 全部 Java 源码 | 结构性命名空间，改动需调整所有 import |
| 传输加密密钥 | 前端 `src/lib/encrypt.ts`、后端 `ApiEncryptServiceAesImpl` / `ApiEncryptServiceSmImpl` | 功能性常量，改动会破坏登录口令加密 |
| 数据库注释 | `V5__sa_system_remaining_tables.sql` | 属已冻结的 Flyway 迁移，改动会使既有库校验失败 |
