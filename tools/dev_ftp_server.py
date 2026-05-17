#!/usr/bin/env python3
import argparse
import os
import socket
import socketserver
import threading
from pathlib import Path


class FtpState:
    def __init__(self, root):
        self.root = root.resolve()
        self.cwd = Path("/")
        self.rest_offset = 0
        self.passive_socket = None


class FtpHandler(socketserver.StreamRequestHandler):
    server_version = "CSNetLabFTP"

    def setup(self):
        super().setup()
        self.state = FtpState(self.server.root)

    def handle(self):
        self.reply(220, f"{self.server_version} ready")
        while True:
            raw = self.rfile.readline()
            if not raw:
                break
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                continue
            command, argument = self.parse_command(line)
            if not self.dispatch(command, argument):
                break

    def finish(self):
        self.close_passive_socket()
        super().finish()

    def parse_command(self, line):
        if " " in line:
            command, argument = line.split(" ", 1)
            return command.upper(), argument.strip()
        return line.upper(), ""

    def dispatch(self, command, argument):
        handlers = {
            "USER": self.handle_user,
            "PASS": self.handle_pass,
            "SYST": lambda _: self.reply(215, "UNIX Type: L8"),
            "FEAT": self.handle_feat,
            "PWD": self.handle_pwd,
            "XPWD": self.handle_pwd,
            "TYPE": lambda arg: self.reply(200, f"Type set to {arg or 'A'}"),
            "CWD": self.handle_cwd,
            "CDUP": lambda _: self.handle_cwd(".."),
            "PASV": self.handle_pasv,
            "LIST": self.handle_list,
            "SIZE": self.handle_size,
            "REST": self.handle_rest,
            "RETR": self.handle_retr,
            "STOR": self.handle_stor,
            "APPE": self.handle_appe,
            "QUIT": self.handle_quit,
            "NOOP": lambda _: self.reply(200, "OK"),
        }
        handler = handlers.get(command)
        if handler is None:
            self.reply(502, f"Command {command} not implemented")
            return True
        result = handler(argument)
        return True if result is None else result

    def handle_user(self, _argument):
        self.reply(331, "User name okay, need password")

    def handle_pass(self, _argument):
        self.reply(230, "Login successful")

    def handle_feat(self, _argument):
        self.wfile.write(b"211-Features\r\n")
        self.wfile.write(b" REST STREAM\r\n")
        self.wfile.write(b" SIZE\r\n")
        self.wfile.write(b"211 End\r\n")
        self.wfile.flush()

    def handle_pwd(self, _argument=""):
        self.reply(257, f'"{self.ftp_path(self.state.cwd)}" is current directory')

    def handle_cwd(self, argument):
        target = self.resolve_path(argument or "/")
        if target.is_dir():
            self.state.cwd = Path("/") / target.relative_to(self.state.root)
            self.reply(250, "Directory changed")
        else:
            self.reply(550, "Directory not found")

    def handle_pasv(self, _argument):
        self.close_passive_socket()
        passive = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        passive.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        passive.bind((self.server.passive_host, 0))
        passive.listen(1)
        self.state.passive_socket = passive

        host = self.server.advertise_host
        port = passive.getsockname()[1]
        nums = host.split(".") + [str(port // 256), str(port % 256)]
        self.reply(227, "Entering Passive Mode (" + ",".join(nums) + ")")

    def handle_list(self, argument):
        target = self.resolve_path(argument or ".")
        if not target.exists():
            self.reply(550, "Path not found")
            return
        self.reply(150, "Opening ASCII mode data connection for file list")
        with self.open_data_connection() as data:
            if target.is_dir():
                lines = [self.format_list_item(item) for item in sorted(target.iterdir())]
            else:
                lines = [self.format_list_item(target)]
            payload = ("\r\n".join(lines) + "\r\n").encode("utf-8")
            data.sendall(payload)
        self.reply(226, "Transfer complete")

    def handle_size(self, argument):
        target = self.resolve_path(argument)
        if target.is_file():
            self.reply(213, str(target.stat().st_size))
        else:
            self.reply(550, "File not found")

    def handle_rest(self, argument):
        try:
            offset = int(argument)
            if offset < 0:
                raise ValueError()
        except ValueError:
            self.reply(501, "Invalid restart position")
            return
        self.state.rest_offset = offset
        self.reply(350, f"Restarting at {offset}")

    def handle_retr(self, argument):
        target = self.resolve_path(argument)
        if not target.is_file():
            self.reply(550, "File not found")
            return
        self.reply(150, "Opening binary mode data connection")
        with self.open_data_connection() as data:
            with target.open("rb") as file:
                file.seek(self.state.rest_offset)
                while True:
                    chunk = file.read(64 * 1024)
                    if not chunk:
                        break
                    data.sendall(chunk)
        self.state.rest_offset = 0
        self.reply(226, "Transfer complete")

    def handle_stor(self, argument):
        self.receive_file(argument, append=False)

    def handle_appe(self, argument):
        self.receive_file(argument, append=True)

    def receive_file(self, argument, append):
        target = self.resolve_path(argument)
        target.parent.mkdir(parents=True, exist_ok=True)
        mode = "ab" if append else "r+b" if target.exists() and self.state.rest_offset else "wb"
        self.reply(150, "Opening binary mode data connection")
        with self.open_data_connection() as data:
            with target.open(mode) as file:
                if self.state.rest_offset:
                    file.seek(self.state.rest_offset)
                while True:
                    chunk = data.recv(64 * 1024)
                    if not chunk:
                        break
                    file.write(chunk)
        self.state.rest_offset = 0
        self.reply(226, "Transfer complete")

    def handle_quit(self, _argument):
        self.reply(221, "Goodbye")
        return False

    def open_data_connection(self):
        if self.state.passive_socket is None:
            raise RuntimeError("PASV must be sent before data command")
        passive = self.state.passive_socket
        self.state.passive_socket = None
        conn, _addr = passive.accept()
        passive.close()
        return conn

    def close_passive_socket(self):
        if self.state.passive_socket is not None:
            self.state.passive_socket.close()
            self.state.passive_socket = None

    def resolve_path(self, ftp_path):
        if not ftp_path:
            ftp_path = "."
        candidate = Path(ftp_path)
        if candidate.is_absolute():
            relative = Path(*candidate.parts[1:])
        else:
            relative = Path(*self.state.cwd.parts[1:]) / candidate
        resolved = (self.state.root / relative).resolve()
        if self.state.root != resolved and self.state.root not in resolved.parents:
            return self.state.root
        return resolved

    def ftp_path(self, path):
        text = "/" + str(path).replace("\\", "/").strip("/")
        return "/" if text == "/" else text

    def format_list_item(self, path):
        file_type = "d" if path.is_dir() else "-"
        size = path.stat().st_size
        name = path.name
        return f"{file_type}rw-r--r-- 1 owner group {size:>8} Jan 01 00:00 {name}"

    def reply(self, code, message):
        self.wfile.write(f"{code} {message}\r\n".encode("utf-8"))
        self.wfile.flush()


class ThreadedFtpServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, server_address, handler_class, root, passive_host, advertise_host):
        super().__init__(server_address, handler_class)
        self.root = root
        self.passive_host = passive_host
        self.advertise_host = advertise_host


def main():
    parser = argparse.ArgumentParser(description="Development FTP server for CS Net LAB")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=2121)
    parser.add_argument("--root", default="ftp-root")
    args = parser.parse_args()

    root = Path(args.root)
    root.mkdir(parents=True, exist_ok=True)
    (root / "downloads").mkdir(exist_ok=True)
    sample = root / "hello.txt"
    if not sample.exists():
        sample.write_text("Hello from the CS Net LAB development FTP server.\n", encoding="utf-8")

    with ThreadedFtpServer((args.host, args.port), FtpHandler, root, args.host, args.host) as server:
        print(f"FTP server listening on {args.host}:{args.port}, root={root.resolve()}", flush=True)
        shutdown = threading.Event()
        try:
            while not shutdown.is_set():
                server.handle_request()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()

