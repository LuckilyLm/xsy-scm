# 公开溯源接口

- 公开查询凭包装标签令牌读取脱敏内容，调用请求层必须声明 anonymous，不携带客户登录令牌，也不触发客户登录守卫。
- 实现位于pages-sub/shared/api/trace/trace-public-api.js，仅由业务分包读取；本目录不再保留主包接口副本。
