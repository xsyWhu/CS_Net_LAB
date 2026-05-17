package com.csnetlab.ftp;

import com.csnetlab.ftp.core.FtpClient;
import com.csnetlab.ftp.core.FtpFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FtpSmokeTest {
    private FtpSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2121;

        Path tmpDir = Path.of("tmp");
        Files.createDirectories(tmpDir);
        Path downloadFile = tmpDir.resolve("java-download-hello.txt");
        Path resumeDownloadFile = tmpDir.resolve("java-resume-download-hello.txt");
        Path uploadFile = tmpDir.resolve("java-upload.txt");
        Files.writeString(uploadFile, "upload from Java FtpClient smoke test\n");

        try (FtpClient client = new FtpClient()) {
            System.out.println(client.connect(host, port).message());
            System.out.println(client.login("test", "test").message());
            System.out.println(client.pwd().message());

            List<FtpFile> files = client.listFiles();
            System.out.println("LIST count=" + files.size());
            for (FtpFile file : files) {
                System.out.println(file);
            }

            client.download("hello.txt", downloadFile, false, (done, total) -> {
            });
            System.out.println("downloaded " + Files.size(downloadFile) + " bytes");

            Files.write(resumeDownloadFile, Files.readAllBytes(downloadFile));
            byte[] original = Files.readAllBytes(resumeDownloadFile);
            Files.write(resumeDownloadFile, java.util.Arrays.copyOf(original, Math.min(10, original.length)));
            client.download("hello.txt", resumeDownloadFile, true, (done, total) -> {
            });
            System.out.println("resume downloaded " + Files.size(resumeDownloadFile) + " bytes");

            client.upload(uploadFile, "java-upload.txt", false, (done, total) -> {
            });
            System.out.println("uploaded size=" + client.size("java-upload.txt"));

            Path resumeUploadFile = tmpDir.resolve("java-resume-upload.txt");
            Path partialUploadFile = tmpDir.resolve("java-resume-upload.partial.txt");
            String fullUploadContent = "resume upload from Java FtpClient smoke test\n";
            Files.writeString(resumeUploadFile, fullUploadContent);
            Files.writeString(partialUploadFile, fullUploadContent.substring(0, 12));
            client.upload(partialUploadFile, "java-resume-upload.txt", false, (done, total) -> {
            });
            client.upload(resumeUploadFile, "java-resume-upload.txt", true, (done, total) -> {
            });
            System.out.println("resume upload size=" + client.size("java-resume-upload.txt"));
        }
    }
}
