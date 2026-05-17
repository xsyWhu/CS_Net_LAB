# 计算机网络实践 FTP 客户端

本项目是 2026 年计算机网络实践的软件设计项目：使用原生 Socket 编程实现一个带图形界面的 FTP 客户端。

项目支持连接 FTP 服务器、远程目录浏览、上传、下载、断点续传和操作日志显示。开发环境可以是 WSL2 + VSCode，最终程序可以打包成 jar 在 Windows 上运行。

## 功能

- FTP 服务器连接、登录、断开
- 远程目录浏览
- 本地文件选择
- 文件上传
- 文件下载
- 断点续传下载
- 断点续传上传
- 传输进度条
- 操作日志显示
- 内置本地 FTP 测试服务器

## 技术栈

- Java 17+
- Swing 图形界面
- `java.net.Socket` 实现 FTP 控制连接和数据连接
- Python 3，仅用于启动本地 FTP 测试服务器
- 不依赖 Maven、Gradle 或第三方 Java 库

## 目录结构

```text
.
├── README.md
├── docs/
│   └── implementation-plan.md
├── scripts/
│   ├── build.sh
│   ├── package.sh
│   ├── run.sh
│   ├── start-dev-ftp.sh
│   └── stop-dev-ftp.sh
├── src/main/java/com/csnetlab/ftp/
│   ├── FtpClientApp.java
│   ├── FtpSmokeTest.java
│   ├── core/
│   │   ├── FtpClient.java
│   │   ├── FtpFile.java
│   │   ├── FtpReply.java
│   │   └── TransferProgress.java
│   └── ui/
│       └── MainFrame.java
└── tools/
    └── dev_ftp_server.py
```

## 从 0 开始运行

### 1. 安装环境

需要安装：

- JDK 17 或更高版本
- Python 3
- Git

在 WSL2 Ubuntu 中可以执行：

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk python3 git
```

检查版本：

```bash
java -version
javac -version
python3 --version
```

### 2. 获取代码

如果是从 Git 仓库获取：

```bash
git clone <项目仓库地址>
cd CS_Net_LAB
```

如果是组员之间直接传压缩包，解压后进入项目根目录即可。

### 3. 启动本地 FTP 测试服务器

打开第一个终端，进入项目根目录，执行：

```bash
python3 tools/dev_ftp_server.py --host 127.0.0.1 --port 2121 --root ftp-root
```

看到类似输出即表示服务端启动成功：

```text
FTP server listening on 127.0.0.1:2121, root=...
```

这个终端不要关闭。测试服务器连接参数如下：

```text
主机：127.0.0.1
端口：2121
用户名：test
密码：test
```

用户名和密码可以任意填写，测试服务器都会允许登录。

### 4. 编译项目

打开第二个终端，进入项目根目录，执行：

```bash
./scripts/build.sh
```

编译成功后会生成 `out/` 目录。

如果脚本没有执行权限，先执行：

```bash
chmod +x scripts/*.sh
```

### 5. 运行图形界面

```bash
./scripts/run.sh
```

也可以直接执行：

```bash
java -cp out com.csnetlab.ftp.FtpClientApp
```

在 GUI 中填写：

```text
主机：127.0.0.1
端口：2121
用户：test
密码：test
```

点击“连接”后，远程目录列表会显示测试服务器中的文件。

## 功能测试步骤

### 连接和目录浏览

1. 启动本地 FTP 测试服务器。
2. 启动 GUI 客户端。
3. 输入 `127.0.0.1`、`2121`、`test`、`test`。
4. 点击“连接”。
5. 查看远程目录列表和日志窗口。

### 下载文件

1. 在远程目录列表中选择 `hello.txt`。
2. 点击“下载”。
3. 选择保存位置。
4. 查看进度条和日志。

### 上传文件

1. 点击“选择本地文件”。
2. 选择任意本地文件。
3. 点击“上传”。
4. 上传完成后远程目录会刷新。

### 断点续传下载

1. 先下载一次 `hello.txt`。
2. 手动保留一个不完整的本地文件，或者在下载大文件中断后保留残缺文件。
3. 再次选择远程文件。
4. 点击“续传下载”并选择同一个本地文件。
5. 客户端会根据本地文件大小发送 `REST` 命令后继续下载。

### 断点续传上传

1. 先上传一个不完整文件到服务器，或中断一次上传。
2. 选择完整的本地文件。
3. 点击“续传上传”。
4. 客户端会先查询远程文件大小，再发送 `REST` 命令继续上传。

## 命令行冒烟测试

如果当前环境不能显示 Swing 窗口，可以使用命令行测试客户端核心功能：

```bash
./scripts/build.sh
java -cp out com.csnetlab.ftp.FtpSmokeTest 127.0.0.1 2121
```

成功时会看到类似输出：

```text
220 CSNetLabFTP ready
230 Login successful
257 "/" is current directory
LIST count=...
downloaded ... bytes
resume downloaded ... bytes
uploaded size=...
resume upload size=...
```

## 打包为 jar

生成 Windows 可运行 jar：

```bash
./scripts/package.sh
```

生成文件：

```text
dist/cs-net-lab-ftp-client.jar
```

运行 jar：

```bash
java -jar dist/cs-net-lab-ftp-client.jar
```

Windows 上运行时，需要先安装 JDK 或 JRE。可以在 Windows 的 PowerShell 中进入项目目录后执行同样的 `java -jar` 命令。

## WSL2 和 Windows 说明

如果在 WSL2 中运行 GUI，可能会遇到无法显示窗口的问题。这取决于 Windows 版本和 WSLg 是否可用。

推荐方式：

- 在 WSL2 中编译、测试命令行功能。
- 使用 `./scripts/package.sh` 生成 jar。
- 在 Windows 中运行 `java -jar dist/cs-net-lab-ftp-client.jar` 展示 GUI。

如果要让 Windows GUI 连接 WSL2 中启动的 FTP 测试服务器，优先尝试：

```text
主机：127.0.0.1
端口：2121
```

如果连接失败，可以在 WSL2 中查看 IP：

```bash
hostname -I
```

然后在 Windows GUI 中把主机改成查到的 WSL2 IP。

## 常见问题

### Permission denied

脚本没有执行权限时执行：

```bash
chmod +x scripts/*.sh
```

### Address already in use

说明 `2121` 端口已被占用。可以换一个端口，例如：

```bash
python3 tools/dev_ftp_server.py --host 127.0.0.1 --port 2021 --root ftp-root
```

GUI 中端口也要改成 `2021`。

### Connection refused

通常是 FTP 测试服务器没有启动，或者 GUI 填写的端口不一致。先确认服务端终端仍在运行。

### GUI 没有显示

WSL2 图形环境不可用时，改在 Windows 侧运行 jar：

```bash
java -jar dist/cs-net-lab-ftp-client.jar
```

### javac: command not found

说明没有安装 JDK，或环境变量没有配置好。Ubuntu/WSL2 中执行：

```bash
sudo apt install -y openjdk-17-jdk
```

## 实验报告可用描述

本项目通过 `Socket` 建立 FTP 控制连接，向服务器发送 FTP 命令并读取响应码。目录浏览和文件传输使用被动模式 `PASV` 创建数据连接。下载使用 `RETR`，上传使用 `STOR`，断点续传通过 `REST` 命令指定文件偏移量后继续传输。图形界面使用 Swing 实现，网络操作放在后台线程中执行，避免界面阻塞。
