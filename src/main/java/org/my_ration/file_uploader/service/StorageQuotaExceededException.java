package org.my_ration.file_uploader.service;

import lombok.Getter;

import java.io.IOException;

/** Файл не помещается в лимит хранилища пользователя. */
@Getter
public class StorageQuotaExceededException extends IOException {

    public static final String CODE = "no_available_storage";

    private final long usedBytes;
    private final long fileSizeBytes;
    private final long limitBytes;

    public StorageQuotaExceededException(long usedBytes, long fileSizeBytes, long limitBytes) {
        super(CODE);
        this.usedBytes = usedBytes;
        this.fileSizeBytes = fileSizeBytes;
        this.limitBytes = limitBytes;
    }
}
