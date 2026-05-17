# 功能截图执行清单

用于实验报告截图。建议在 Windows 上运行 GUI，这样截图更稳定。

## 1. 启动测试服务器

在项目根目录打开终端：

```bash
python3 tools/dev_ftp_server.py --host 127.0.0.1 --port 2121 --root ftp-root
```

保持该终端不关闭。

## 2. 启动客户端

另开终端：

```bash
./scripts/package.sh
java -jar dist/cs-net-lab-ftp-client.jar
```

GUI 中填写：

```text
主机：127.0.0.1
端口：2121
用户：test
密码：test
```

## 3. 截图列表

| 完成 | 文件名 | 截图时机 |
| --- | --- | --- |
| [ ] | `01-main-window.png` | 客户端刚打开，显示完整主界面 |
| [ ] | `02-connect-success.png` | 点击连接后，日志显示登录成功 |
| [ ] | `03-list-files.png` | 远程目录列表显示 `hello.txt` |
| [ ] | `04-download.png` | 下载 `hello.txt` 完成后 |
| [ ] | `05-upload.png` | 上传一个本地文件完成，远程列表刷新后 |
| [ ] | `06-resume-download.png` | 续传下载完成后 |
| [ ] | `07-resume-upload.png` | 续传上传完成后 |
| [ ] | `08-smoke-test.png` | 终端运行 `FtpSmokeTest` 输出成功结果 |

## 4. 命令行测试截图

```bash
./scripts/build.sh
java -cp out com.csnetlab.ftp.FtpSmokeTest 127.0.0.1 2121
```

截图中应包含：

```text
220 CSNetLabFTP ready
230 Login successful
downloaded ... bytes
resume downloaded ... bytes
uploaded size=...
resume upload size=...
```

