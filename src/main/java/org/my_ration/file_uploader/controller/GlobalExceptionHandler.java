package org.my_ration.file_uploader.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@Log4j2
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidFileType(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        log.error(ex);
        log.debug("Error Message: {}",ex.getMessage());
        if (ex.getMessage().equals("file_not_found")){
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body("Error processing file: " + ex.getMessage());
        } else if(ex.getMessage().equals("no_available_storage")){
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error processing file: " + ex.getMessage());
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + ex.getMessage());
        }
    }
}