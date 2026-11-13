package com.fuzzydl.app.shizuku;

oneway interface IExtractCallback {
    void onProgress(long doneBytes, long totalBytes, int files);
}
