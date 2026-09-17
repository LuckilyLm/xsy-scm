@echo off
REM ============================================================================
REM  XSY-SCM 本地 Docker 一键部署脚本（Windows 双击可用）
REM
REM    deploy.bat build     仅构建镜像
REM    deploy.bat up        构建并启动（默认）
REM    deploy.bat down      停止并移除容器
REM    deploy.bat restart   重启
REM    deploy.bat logs      实时查看日志
REM    deploy.bat status    查看容器状态
REM    deploy.bat clean     停止并清理本项目镜像
REM    deploy.bat db        额外启动容器版 MySQL/Redis
REM ============================================================================
setlocal EnableDelayedExpansion

pushd "%~dp0.."

set COMPOSE_FILE=docker-compose.yml
set DB_FILE=docker-compose.db.yml

REM 识别 docker compose / docker-compose
docker compose version >nul 2>&1
if %errorlevel%==0 (
  set DC=docker compose
) else (
  where docker-compose >nul 2>&1
  if %errorlevel%==0 (
    set DC=docker-compose
  ) else (
    echo [错误] 未找到 docker compose，请先安装/启动 Docker Desktop。
    pause
    exit /b 1
  )
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [错误] Docker 守护进程未运行，请先启动 Docker Desktop。
  pause
  exit /b 1
)

REM 读取 .env 中的端口，便于提示访问地址
set WEB_PORT=8088
set SERVER_PORT=1024
if exist .env (
  for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if "%%a"=="WEB_PORT" set WEB_PORT=%%b
    if "%%a"=="SERVER_PORT" set SERVER_PORT=%%b
  )
)

if "%1"=="" (set CMD=up) else (set CMD=%1)
set ARGS=%*
call set ARGS=%%ARGS:*%1=%%

if "%CMD%"=="build" goto build
if "%CMD%"=="up" goto up
if "%CMD%"=="down" goto down
if "%CMD%"=="restart" goto restart
if "%CMD%"=="logs" goto logs
if "%CMD%"=="status" goto status
if "%CMD%"=="clean" goto clean
if "%CMD%"=="db" goto db

echo 用法: deploy.bat [build^|up^|down^|restart^|logs^|status^|clean^|db]
goto end

:build
%DC% -f %COMPOSE_FILE% build %ARGS%
goto end

:up
%DC% -f %COMPOSE_FILE% up -d --build %ARGS%
echo.
echo 管理后台: http://localhost:%WEB_PORT%/admin/
echo 移动端H5: http://localhost:%WEB_PORT%/h5/
echo 后端接口: http://localhost:%SERVER_PORT%
echo 提示: 首次启动后端需要 1-2 分钟初始化，可用 deploy.bat logs 查看进度
goto end

:down
%DC% -f %COMPOSE_FILE% down %ARGS%
goto end

:restart
%DC% -f %COMPOSE_FILE% restart %ARGS%
goto end

:logs
%DC% -f %COMPOSE_FILE% logs -f --tail=200 %ARGS%
goto end

:status
%DC% -f %COMPOSE_FILE% ps
goto end

:clean
%DC% -f %COMPOSE_FILE% down --rmi local -v
goto end

:db
%DC% -f %COMPOSE_FILE% -f %DB_FILE% up -d --build %ARGS%
echo 容器版 MySQL/Redis 已启动，记得把 .env 中 MYSQL/REDIS 地址改为容器名 mysql / redis
goto end

:end
popd
if /i not "%CMD%"=="logs" pause
endlocal
