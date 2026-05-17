package com.csnetlab.ftp.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

public final class FtpClient implements Closeable {
    private static final int BUFFER_SIZE = 64 * 1024;

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String controlHost;

    public FtpReply connect(String host, int port) throws IOException {
        controlHost = host;
        controlSocket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));
        return readReply();
    }

    public FtpReply login(String username, String password) throws IOException {
        FtpReply userReply = sendCommand("USER " + username);
        if (userReply.code() == 331) {
            return sendCommand("PASS " + password);
        }
        return userReply;
    }

    public FtpReply pwd() throws IOException {
        return sendCommand("PWD");
    }

    public FtpReply cwd(String path) throws IOException {
        return sendCommand("CWD " + path);
    }

    public List<FtpFile> listFiles() throws IOException {
        sendCommandExpectPositive("TYPE I");
        try (Socket dataSocket = openPassiveDataSocket()) {
            FtpReply start = sendCommand("LIST");
            if (start.code() >= 400) {
                throw new IOException(start.message());
            }

            List<FtpFile> files = new ArrayList<>();
            try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = dataReader.readLine()) != null) {
                    files.add(FtpFile.fromListLine(line));
                }
            }

            FtpReply end = readReply();
            if (end.code() >= 400) {
                throw new IOException(end.message());
            }
            return files;
        }
    }

    public long size(String remoteFile) throws IOException {
        FtpReply reply = sendCommand("SIZE " + remoteFile);
        if (reply.code() != 213) {
            return -1L;
        }
        String[] parts = reply.message().split("\\s+");
        if (parts.length < 2) {
            return -1L;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    public void download(String remoteFile, Path localFile, boolean resume, TransferProgress progress) throws IOException {
        sendCommandExpectPositive("TYPE I");
        long remoteSize = size(remoteFile);
        long offset = resume && Files.exists(localFile) ? Files.size(localFile) : 0L;
        if (offset > 0) {
            sendCommandExpectPositive("REST " + offset);
        }

        long expectedBytes = remoteSize > offset ? remoteSize - offset : -1L;
        try (Socket dataSocket = openPassiveDataSocket()) {
            FtpReply start = sendCommand("RETR " + remoteFile);
            if (start.code() >= 400) {
                throw new IOException(start.message());
            }

            if (localFile.getParent() != null) {
                Files.createDirectories(localFile.getParent());
            }
            StandardOpenOption[] options = offset > 0
                    ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

            try (InputStream input = dataSocket.getInputStream();
                 OutputStream output = Files.newOutputStream(localFile, options)) {
                copy(input, output, expectedBytes, progress);
            }

            FtpReply end = readReply();
            if (end.code() >= 400) {
                throw new IOException(end.message());
            }
        }
    }

    public void upload(Path localFile, String remoteFile, boolean resume, TransferProgress progress) throws IOException {
        sendCommandExpectPositive("TYPE I");
        long fileSize = Files.size(localFile);
        long offset = resume ? Math.max(0L, size(remoteFile)) : 0L;
        if (offset > fileSize) {
            offset = 0L;
        }
        if (offset > 0) {
            sendCommandExpectPositive("REST " + offset);
        }

        try (Socket dataSocket = openPassiveDataSocket()) {
            FtpReply start = sendCommand("STOR " + remoteFile);
            if (start.code() >= 400) {
                throw new IOException(start.message());
            }

            try (InputStream input = Files.newInputStream(localFile);
                 OutputStream output = dataSocket.getOutputStream()) {
                skipFully(input, offset);
                copy(input, output, fileSize - offset, progress);
            }

            FtpReply end = readReply();
            if (end.code() >= 400) {
                throw new IOException(end.message());
            }
        }
    }

    public FtpReply sendCommand(String command) throws IOException {
        ensureConnected();
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
        return readReply();
    }

    private FtpReply sendCommandExpectPositive(String command) throws IOException {
        FtpReply reply = sendCommand(command);
        if (!reply.isPositive()) {
            throw new IOException(reply.message());
        }
        return reply;
    }

    private Socket openPassiveDataSocket() throws IOException {
        FtpReply reply = sendCommand("PASV");
        if (reply.code() != 227) {
            throw new IOException("Server did not enter passive mode: " + reply.message());
        }

        InetSocketAddress address = parsePassiveAddress(reply.message());
        Socket dataSocket = new Socket();
        dataSocket.connect(address, 10_000);
        return dataSocket;
    }

    private InetSocketAddress parsePassiveAddress(String message) throws IOException {
        int left = message.indexOf('(');
        int right = message.indexOf(')', left + 1);
        if (left < 0 || right < 0) {
            throw new IOException("Invalid PASV reply: " + message);
        }

        String[] parts = message.substring(left + 1, right).split(",");
        if (parts.length != 6) {
            throw new IOException("Invalid PASV reply: " + message);
        }

        try {
            String host = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
            if ("0.0.0.0".equals(host)) {
                host = controlHost;
            }
            return new InetSocketAddress(host, port);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid PASV reply: " + message, ex);
        }
    }

    private void copy(InputStream input, OutputStream output, long totalBytes, TransferProgress progress) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long transferred = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            transferred += read;
            if (progress != null) {
                progress.onProgress(transferred, totalBytes);
            }
        }
        output.flush();
    }

    private void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) {
                    throw new EOFException("Cannot skip to upload resume offset.");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private FtpReply readReply() throws IOException {
        ensureConnected();
        String firstLine = reader.readLine();
        if (firstLine == null) {
            throw new IOException("FTP server closed the control connection.");
        }

        StringBuilder message = new StringBuilder(firstLine);
        int code = parseReplyCode(firstLine);
        if (firstLine.length() >= 4 && firstLine.charAt(3) == '-') {
            String expectedPrefix = firstLine.substring(0, 3) + " ";
            String line;
            while ((line = reader.readLine()) != null) {
                message.append(System.lineSeparator()).append(line);
                if (line.startsWith(expectedPrefix)) {
                    break;
                }
            }
        }
        return new FtpReply(code, message.toString());
    }

    private int parseReplyCode(String line) throws IOException {
        if (line.length() < 3) {
            throw new IOException("Invalid FTP reply: " + line);
        }
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid FTP reply code: " + line, ex);
        }
    }

    private void ensureConnected() throws IOException {
        if (controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("FTP control connection is not open.");
        }
    }

    @Override
    public void close() throws IOException {
        if (controlSocket != null && !controlSocket.isClosed()) {
            try {
                sendCommand("QUIT");
            } catch (IOException ignored) {
                // Closing the socket below is enough if QUIT fails.
            }
            controlSocket.close();
        }
    }
}
