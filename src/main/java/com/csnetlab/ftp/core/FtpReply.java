package com.csnetlab.ftp.core;

public record FtpReply(int code, String message) {
    public boolean isPositive() {
        return code >= 100 && code < 400;
    }
}

