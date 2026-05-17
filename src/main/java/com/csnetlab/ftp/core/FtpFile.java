package com.csnetlab.ftp.core;

public record FtpFile(String name, boolean directory, long size, String rawLine) {
    public static FtpFile fromListLine(String line) {
        boolean directory = line.startsWith("d");
        String[] parts = line.trim().split("\\s+", 9);
        long size = -1L;
        String name = line;
        if (parts.length >= 9) {
            name = parts[8];
            try {
                size = Long.parseLong(parts[4]);
            } catch (NumberFormatException ignored) {
                size = -1L;
            }
        }
        return new FtpFile(name, directory, size, line);
    }

    @Override
    public String toString() {
        if (directory) {
            return "[DIR] " + name;
        }
        return size >= 0 ? name + " (" + size + " bytes)" : name;
    }
}

