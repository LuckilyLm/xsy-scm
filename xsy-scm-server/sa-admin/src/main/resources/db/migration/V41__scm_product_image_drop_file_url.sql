-- V41: 商品图不再持久化文件访问地址（F0-DEBT-01 裁决的写侧收口），2026-09-21。
--
-- 为什么要删这一列：`product_image.file_url` 存的是 `FileService` 当时生成的**预签名 URL**
-- （私有目录对象带签名串与有效期，云端配置里还含 bucket / endpoint），而读路径
-- （`ProductQueryService`）每次响应都按 `file_key` 重新生成并覆盖它 —— 即这一列
-- 从来不是任何展示口径的来源，只是一份会过期、且随存储配置漂移的副本。
-- 把它留在库里等于：过期链接被导出与备份带走、换 bucket 或换环境后整列变成死数据。
--
-- 删除是安全的：`file_key` 是唯一事实，URL 可由它随时重算，因此本迁移不丢任何业务数据。
-- 刻意**不**给 `file_key` 加前缀 CHECK —— 本裁决之前的存量行是 `private/common/` 下的
-- 合法上传结果，加 CHECK 会让历史商品直接校验失败；「新绑定只能落在公开图片目录」
-- 由 `ProductImageSyncManager.requirePublicImageKey` 在写入口执行，存量行沿用原 key 不受影响。

ALTER TABLE product_image DROP COLUMN file_url;
