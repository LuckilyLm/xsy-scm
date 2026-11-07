# 商品后台

- 分类编辑沿用既有分类权限和版本；图标只保存iconFileKey，公开URL由服务端解析。
- category-form-modal复用文件上传服务，可选择assets/category-icons中的交付PNG或上传图片；预设也必须上传到正式文件服务，不能将打包URL存入分类。
- 原图与来源哈希在assets/category-icons/sources.json，国产和进口水果是不同原图，鱼类继续统一选fish-shrimp。清理重复素材不能合并真实业务分类。
- 上传中禁止保存，取消或切换分类后丢弃旧上传结果；清空图标显式传null。分类表格与小程序读取同一个配置，不能各自猜图。
