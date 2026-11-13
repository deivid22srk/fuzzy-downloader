package com.fuzzydl.app.shizuku;

import com.fuzzydl.app.shizuku.IExtractCallback;

interface IFileService {
    void destroy() = 16777114;
    boolean exists(String path) = 1;
    long freeBytes(String path) = 2;
    boolean mkdirs(String path) = 3;
    String extractZip(String zipPath, String destDir, IExtractCallback cb) = 4;
    boolean delete(String path) = 5;
}
