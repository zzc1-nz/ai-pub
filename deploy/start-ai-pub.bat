@echo off
chcp 65001 >nul
echo ==============================================
echo          AI-Pub 微信公众号管理系统
echo ==============================================
echo.

cd /d "C:\Users\24657\Documents\trae_projects\bishe\deploy"

echo [1/3] 启动后端服务 (MySQL, Redis, RabbitMQ, Backend)...
docker-compose up -d

echo.
echo [2/3] 等待服务启动...
timeout /t 15 /nobreak >nul

echo.
echo [3/3] 启动前端服务...
docker rm -f ai-pub-frontend >nul 2>&1
docker run -d --name ai-pub-frontend --network deploy_ai-pub-network -p 3000:80 deploy-frontend:latest

echo.
echo ==============================================
echo              系统启动完成！
echo ==============================================
echo.
echo 后台管理地址: http://127.0.0.1:3000
echo 用户名: admin
echo 密码: admin123456
echo.
echo 按任意键退出...
pause >nul