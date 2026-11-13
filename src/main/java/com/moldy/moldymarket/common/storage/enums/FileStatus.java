package com.moldy.moldymarket.common.storage.enums;

public enum FileStatus {
    PENDING, // đã tạo presigned url, chờ upload file
    AVAILABLE, // thành công
    REJECTED, //ko đạt validation
    DELETED
}