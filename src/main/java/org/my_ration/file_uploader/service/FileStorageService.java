package org.my_ration.file_uploader.service;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.my_ration.file_uploader.entities.Photo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import javax.imageio.spi.IIORegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    /** Сколько символов исходного имени оставлять в имени файла на диске. */
    private static final int MAX_BASE_NAME_LENGTH = 80;

    @Value("${file.upload.root-directory}")
    private String rootDirectory;

    @Value("${allowed.file.types}")
    private String[] allowedMimeTypes;

    @Value("${allowed.file.categories}")
    private String[] allowedCategories;

    @Value("${allowed.file.targetWidth}")
    private int targetWidth;

    @Value("${allowed.file.targetHeight}")
    private int targetHeight;

    @Value("${allowed.file.maxFileSizeBytes}")
    private long maxFileSizeBytes;

    @Value("${allowed.file.maxPixels}")
    private long maxPixels;

    @Value("${allowed.user.maxUserStorageSize}")
    private long maxUserStorageSize;

    private final UserService userService;
    private final PhotoService photoService;

    private ImageProcessor imageProcessor;

    @PostConstruct
    public void initializeWebPSupport() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new WebPImageReaderSpi());
        imageProcessor = new ImageProcessor(targetWidth, targetHeight, maxFileSizeBytes, maxPixels);
    }

    public String storeFile(MultipartFile file, String category) throws IOException {
        // Валидация типа файла
        if (!isValidFileType(file)) {
            throw new IllegalArgumentException("invalid_file_type");
        }

        // Категория — только из белого списка: иначе «..» уводит запись за пределы корня.
        if (!isAllowedCategory(category)) {
            throw new IllegalArgumentException("no_such_path");
        }
        Path uploadPath = Paths.get(rootDirectory, category);
        if (!Files.exists(uploadPath)) {
            throw new IllegalArgumentException("no_such_path");
        }

        log.debug("Start compress");
        ProcessedImage image = imageProcessor.process(file.getBytes());
        long fileSize = image.data().length;
        log.debug("FileSize: {}", fileSize);

        Path filePath = uploadPath.resolve(storedFileName(file.getOriginalFilename(), image.extension()));

        log.debug("Start get user");
        User user = userService.getUser();

        Long userStorageSize = photoService.getUserCurrentStorageSizeByUIID(user.getUserUIID());

        log.debug("CurrentUserStorageSize: {}", userStorageSize);

        if (userStorageSize < maxUserStorageSize - fileSize || user.isSystemRole()) {
            log.debug("SavePhoto");
            Files.write(filePath, image.data());

            Photo photo = new Photo();
            photo.setUserUIID(user.getUserUIID());
            photo.setPath(filePath.toString());
            photo.setSize(fileSize);
            photo.setCategory(category);

            photoService.saveUserPhoto(photo);
        } else {
            throw new IOException("no_available_storage");
        }

        return filePath.toString();
    }

    /**
     * Имя файла на диске. Расширение — от настоящего формата: по нему nginx
     * выбирает Content-Type, и «картинка» с именем x.html ушла бы как text/html.
     * Из исходного имени остаётся только основа — для читаемости.
     */
    static String storedFileName(String originalName, String extension) {
        String base = originalName == null ? "" : originalName;
        int dot = base.lastIndexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (base.length() > MAX_BASE_NAME_LENGTH) {
            base = base.substring(0, MAX_BASE_NAME_LENGTH);
        }
        String prefix = UUID.randomUUID().toString();
        return (base.isEmpty() ? prefix : prefix + "_" + base) + "." + extension;
    }

    private boolean isAllowedCategory(String category) {
        return Arrays.asList(allowedCategories).contains(category);
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
}
