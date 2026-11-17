@echo off
chcp 65001 >nul
echo ====================================================================
echo 正在推送到 GitHub 远程仓库: pkkm-123456/byd-qianqian-player
echo ====================================================================
cd /d "D:\Project\byd-qianqian-player"
"C:\Program Files\Git\cmd\git.exe" -c "safe.directory=*" push -u origin main --force
echo.
if %errorlevel% equ 0 (
    echo [成功] 全部48个源文件已成功推送到 GitHub!
    echo GitHub Actions 已自动触发编译，请在网页查看打包状态。
) else (
    echo [提示] 请在弹出的浏览器窗口中点击授权登录 GitHub，授权后按任意键重新运行即可完成推送。
)
pause
