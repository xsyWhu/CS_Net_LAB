# CS Net LAB FTP Client

2026 计算机网络实践项目：基于 Socket 编程的图形化 FTP 客户端。

## 技术选型

- Java 17+
- Swing 图形界面
- 原生 `java.net.Socket` 实现 FTP 控制连接和数据连接
- 不依赖 Maven/Gradle，方便在 WSL2 + VSCode 中开发，并可在 Windows 上运行

## 当前功能规划

- FTP 服务器连接、登录、断开
- 远程目录浏览
- 本地文件选择
- 文件下载
- 文件上传
- 断点续传下载
- 断点续传上传
- 操作日志显示

## 编译运行

```bash
./scripts/build.sh
./scripts/run.sh
```

打包 Windows 可运行 jar：

```bash
./scripts/package.sh
java -jar dist/cs-net-lab-ftp-client.jar
```

如果当前环境不能显示 Swing 窗口，可以先运行命令行冒烟测试：

```bash
java -cp out com.csnetlab.ftp.FtpSmokeTest 127.0.0.1 2121
```

## 本地 FTP 测试服务器

开发时可以启动仓库内置的轻量级 FTP 测试服务器：

```bash
python3 tools/dev_ftp_server.py --host 127.0.0.1 --port 2121 --root ftp-root
```

测试连接参数：

- 主机：`127.0.0.1`
- 端口：`2121`
- 用户名：任意，例如 `test`
- 密码：任意，例如 `test`

测试服务器根目录是 `ftp-root`，启动时会自动创建示例文件 `hello.txt`。
