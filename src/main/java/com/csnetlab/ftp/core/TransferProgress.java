package com.csnetlab.ftp.core;

@FunctionalInterface
public interface TransferProgress {
    void onProgress(long transferredBytes, long totalBytes);
}
