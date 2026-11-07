# 小程序资源工具

- upload-media.py使用MinIO官方SDK发布media清单中的原始PNG；按目录摘要保存，不覆盖同名但摘要不同的对象；发布后匿名下载核对文件摘要。
- MinIO认证从XSY_FILE_ACCESS_KEY/XSY_FILE_SECRET_KEY读取，日志只输出公开资源地址和验证结果。
- 不修改业务分包、主包共享逻辑或用户微信配置；线上图片失败兜底由公共xsy-remote-image组件维护。
- verify-media.mjs只读检查微信构建目录，确认本地小图、基础代码、媒体与主包体积及按需注入；它不修改或搬移打包产物。
- verify-package.mjs从app和主包页面及组件收集入口，检查JS引用闭包、文件存在、包边界和包体积；只读验证，不改写Rollup或构建产物。
- 包检查先独立计算主包可达JS，再校验全部分包页面与组件的JS、JSON、WXML及组件归属；不能让分包入口掩盖主包闲置模块。
