package org.my_ration.file_uploader.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.my_ration.file_uploader.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Log4j2
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;


    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category) throws IOException {

        log.debug("Start storeFile:{} {}", file.getOriginalFilename(), file.getSize());
        String fileName = fileStorageService.storeFile(file, category);


        return ResponseEntity.ok(Map.of(
                "status", "success",
                "fileUrl", fileName
        ));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(
            @RequestParam("filePath") String filePath) throws IOException {

        log.debug("Start deleteFile:{} ", filePath);
        Boolean status = fileStorageService.deleteFile(filePath);
        log.debug("Delete success");
        return ResponseEntity.ok(Map.of(
                "status", "success"
        ));
    }
}