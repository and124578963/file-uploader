package org.my_ration.file_uploader.controller;

import org.my_ration.file_uploader.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category) throws IOException {
        //TODO: заменить на log.info()
        System.out.println("Start fileStorageService:" + file.getOriginalFilename() +" " + file.getSize());
        String fileName = fileStorageService.storeFile(file, category);


        return ResponseEntity.ok(Map.of(
                "status", "success",
                "fileUrl", fileName
        ));
    }
}