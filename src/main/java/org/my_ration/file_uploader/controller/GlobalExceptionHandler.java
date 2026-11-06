package org.my_ration.file_uploader.controller;

import lombok.extern.slf4j.Slf4j;
import org.my_ration.file_uploader.service.StorageQuotaExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidFileType(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("file_too_large");
    }

    /** 507 и JSON с кодом, чтобы клиент отличал переполнение хранилища от запрета доступа. */
    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleStorageQuota(StorageQuotaExceededException ex) {
        log.warn("Storage quota exceeded: used={}, file={}, limit={}",
                ex.getUsedBytes(), ex.getFileSizeBytes(), ex.getLimitBytes());
        return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body(Map.of(
                "status", "error",
                "code", StorageQuotaExceededException.CODE,
                "message", "Превышен лимит хранилища: занято %d из %d байт, размер файла %d байт"
                        .formatted(ex.getUsedBytes(), ex.getLimitBytes(), ex.getFileSizeBytes()),
                "usedBytes", ex.getUsedBytes(),
                "fileSizeBytes", ex.getFileSizeBytes(),
                "limitBytes", ex.getLimitBytes()
        ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        log.error("error: ", ex);
        String message = ex.getMessage();
        HttpStatus status;
        if ("file_not_found".equals(message)) {
            status = HttpStatus.NOT_ACCEPTABLE;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body("Error processing file: " + message);
    }

    /** Всё остальное, включая Error, которые Spring доставляет обёрнутыми в ServletException. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleOther(Exception ex) {
        log.error("error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing file: " + ex.getMessage());
    }
}
