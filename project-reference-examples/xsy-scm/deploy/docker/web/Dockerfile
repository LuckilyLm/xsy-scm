# XSY-SCM 前端镜像（多阶段构建）
# 阶段一：Node 构建 PC 管理后台（/admin）
# 阶段二：Node 构建移动端 H5（/h5）
# 阶段三：Nginx 托管静态资源，/api 反向代理到后端
#
# 构建上下文：仓库根目录
#   docker build -f deploy/docker/web/Dockerfile -t xsy-scm-web:dev .

# ------------------------------------------------------- 1. PC 管理后台
FROM node:20-alpine AS admin-build

WORKDIR /src

COPY xsy-scm-web/package.json xsy-scm-web/package-lock.json ./
RUN npm ci --no-audit --no-fund --registry=https://registry.npmmirror.com \
    || npm install --no-audit --no-fund --registry=https://registry.npmmirror.com

COPY xsy-scm-web ./

# 接口地址：默认走同源 /api，由 Nginx 反代到后端；直连后端时可传 http://host:1024
ARG VITE_APP_API_URL=/api
ENV VITE_APP_API_URL=${VITE_APP_API_URL} \
    NODE_OPTIONS=--max-old-space-size=4096

RUN npx vite build --mode docker --base=/admin/ \
    && test -f dist/index.html

# ------------------------------------------------------- 2. 移动端 H5
FROM node:20-alpine AS h5-build

WORKDIR /src

COPY xsy-app/package.json xsy-app/package-lock.json ./
RUN npm ci --no-audit --no-fund --registry=https://registry.npmmirror.com \
    || npm install --no-audit --no-fund --registry=https://registry.npmmirror.com

COPY xsy-app ./

ARG VITE_APP_API_URL=/api
ENV VITE_APP_API_URL=${VITE_APP_API_URL}

RUN npx uni build --mode docker --base /h5/ \
    && test -f dist/build/h5/index.html

# ------------------------------------------------------- 3. Nginx 运行
FROM nginx:1.27-alpine

ENV TZ=Asia/Shanghai

RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && echo ${TZ} > /etc/timezone

# 兜底默认配置：compose 会挂载 deploy/nginx/nginx.conf 覆盖它
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=admin-build /src/dist /usr/share/nginx/html/admin
COPY --from=h5-build /src/dist/build/h5 /usr/share/nginx/html/h5

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/admin/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
