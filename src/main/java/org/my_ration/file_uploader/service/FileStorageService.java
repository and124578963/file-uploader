package org.my_ration.file_uploader.service;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.my_ration.file_uploader.entities.Photo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    @Value("${file.upload.root-directory}")
    private String rootDirectory;

    @Value("${allowed.file.types}")
    private String[] allowedMimeTypes;

    @Value("${allowed.file.targetWidth}")
    private int targetWidth;

    @Value("${allowed.file.targetHeight}")
    private int targetHeight;

    @Value("${allowed.file.maxFileSizeBytes}")
    private long maxFileSizeBytes;

    @Value("${allowed.user.maxUserStorageSize}")
    private long maxUserStorageSize;

    private final UserService userService;
    private final PhotoService photoService;

    @PostConstruct
    public void initializeWebPSupport() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new WebPImageReaderSpi());
    }

    public String storeFile(MultipartFile file, String category) throws IOException {
        // Валидация типа файла
        if (!isValidFileType(file)) {
            throw new IllegalArgumentException("invalid_file_type");
        }

        // Создание директории для категории
        //Path uploadPath = Paths.get(rootDirectory, category);
        // Решили что деректории должны быть предопределены, а не создоваться пользователем
        Path uploadPath = Paths.get(rootDirectory, category);
        if (!Files.exists(uploadPath)) {
            throw new IllegalArgumentException("no_such_path");
        }

        log.debug("File name: {}", uploadPath.getFileName());

        log.debug("Start sanitizeFileName");
        String originalFilename = sanitizeFileName(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(fileName);

        // Создать все отсутствующие директории в пути
        Files.createDirectories(filePath.getParent());

        log.debug("Start compress");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = compressImage(file)) {
            is.transferTo(baos);
        }
        byte[] compressedData = baos.toByteArray();
        long fileSize = compressedData.length;

        //long fileSize = compressedFile.available();
        log.debug("FileSize: {}", fileSize);

        log.debug("Start get user");
        User user = userService.getUser();

        Long userStorageSize=photoService.getUserCurrentStorageSizeByUIID(user.getUserUIID());

        log.debug("CurrentUserStorageSize: {}", userStorageSize);

        if(userStorageSize<maxUserStorageSize-fileSize||user.isSystemRole()) {
            try (InputStream is = new ByteArrayInputStream(compressedData)) {
                log.debug("SavePhoto");
                Files.copy(is, filePath);

                Photo photo = new Photo();
                photo.setUserUIID(user.getUserUIID());
                photo.setPath(filePath.toString());
                photo.setSize(fileSize);
                photo.setCategory(category);

                photoService.saveUserPhoto(photo);
            }
        }else{
            throw new IOException("no_available_storage");
        }


        return filePath.toString(); // Возвращаем относительный путь
    }

    // В FileStorageService
    private String sanitizeFileName(String name) {
        return name.replace("..", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    private boolean isValidFileType(MultipartFile file) {
        String fileContentType = file.getContentType();
        for (String allowedType : allowedMimeTypes) {
            if (allowedType.equalsIgnoreCase(fileContentType)) {
                return true;
            }
        }
        return false;
    }

    public Boolean deleteFile(String filePath) throws IOException {

        log.debug("Start getPhotoByPath");
        Photo photo=photoService.getPhotoByPath(filePath);
        if(photo==null){
            throw new IOException("file_not_found");
        }
        log.debug("Photo found in DB. Photo ID: {}",photo.getId());
        log.debug("Start getUser");
        User user = userService.getUser();
        Path photoPath = Paths.get(filePath);

        if (!Files.exists(photoPath)) {
            throw new IOException("no_such_path");
        }

        if(user.getUserUIID().equals(photo.getUserUIID())){
            log.debug("Start delete file by photoPath");
            Files.delete(photoPath);
            log.debug("Start deletePhotoById");
            photoService.deletePhotoById(photo.getId());
        }else {
            throw new IOException("another_users_photo");
        }

        return true;
    }


    private InputStream compressImage(MultipartFile file) throws IOException {
        // 1. Читаем файл в память ОДИН раз
        byte[] originalImageData = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalImageData));

        if (image == null) {
            throw new IllegalArgumentException("Couldn't read the image");
        }

        // Проверка, нужно ли вообще сжатие
        if (image.getWidth() <= targetWidth && image.getHeight() <= targetHeight && originalImageData.length <= maxFileSizeBytes) {
            return new ByteArrayInputStream(originalImageData);
        }

        // 2. Используем локальные переменные для размеров
        int currentWidth = Math.min(image.getWidth(), this.targetWidth);
        int currentHeight = Math.min(image.getHeight(), this.targetHeight);

        double quality = 1.0;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();


        while (true) {
            outputStream.reset(); // Очищаем поток вывода перед каждой итерацией

            // 3. Всегда используем исходные данные изображения для лучшего качества
            Thumbnails.of(new ByteArrayInputStream(originalImageData))
                    .size(currentWidth, currentHeight)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(outputStream);

            if (outputStream.size() <= maxFileSizeBytes || quality <= 0.1) {
                break; // Выходим из цикла, если размер подходит или качество слишком низкое
            }

            // Уменьшаем качество или размер для следующей итерации
            if (quality > 0.4) { // Сначала пытаемся снизить качество
                quality -= 0.1;
            } else { // Если качество уже низкое, начинаем уменьшать размер
                currentWidth = (int)(currentWidth * 0.9);
                currentHeight = (int)(currentHeight * 0.9);
            }
        }

        if (outputStream.size() > maxFileSizeBytes) {
            throw new IOException("Couldn't compress the file to the target size");
        }

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

}