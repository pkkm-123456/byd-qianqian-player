@echo off
chcp 65001 >nul
echo ====================================================================
echo 正在推送到 GitHub 远程仓库: pkkm-123456/byd-qianqian-player
echo ====================================================================
cd /d "%~dp0"
"C:\Program Files\Git\cmd\git.exe" push -u origin main
echo.
if %errorlevel% equ 0 (
    echo [成功] 代码已成功推送到 GitHub!
    echo GitHub Actions 将自动启动并打包车机 APK 安装包。
) else (
    echo [提示] 若提示鉴权失败，请在弹出的浏览器窗口中完成 GitHub 授权登录，或提供 Personal Access Token。
)
pause
