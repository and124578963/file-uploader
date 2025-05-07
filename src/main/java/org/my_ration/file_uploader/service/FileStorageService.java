package org.my_ration.file_uploader.service;


import org.my_ration.file_uploader.entities.CustomPhoto;
import org.my_ration.file_uploader.user.User;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
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
    private float maxFileSizeBytes;

    @Value("${allowed.user.maxUserStorageSize}")
    private long maxUserStorageSize;

    private final UserService userService;

    public FileStorageService(UserService userService) {
        this.userService = userService;
    }

    public String storeFile(MultipartFile file, String category) throws IOException {
        // Валидация типа файла
        if (!isValidFileType(file)) {
            throw new IllegalArgumentException("Invalid file type");
        }

        // Создание директории для категории
        Path uploadPath = Paths.get(rootDirectory, category);
        System.out.println("File name: "+ uploadPath.getFileName());

        /*//Эта не рабочая проверка она проверяет есть ли уже существующие файлы в хранилище
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist");
        }
         */

        System.out.println("Start sanitizeFileName");
        String originalFilename = sanitizeFileName(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(fileName);

        // Создать все отсутствующие директории в пути
        Files.createDirectories(filePath.getParent());

        System.out.println("Start compress");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = compressImage(file)) {
            is.transferTo(baos);
        }
        byte[] compressedData = baos.toByteArray();
        long fileSize = compressedData.length;

        //long fileSize = compressedFile.available();
        System.out.println("FileSize: " +fileSize);

        System.out.println("Start get user");
        userService.getUserInfo();
        Long userStorageSize = userService.getUserStorage();
        System.out.println("CurrentUserStorageSize: " + userStorageSize);

        User user= userService.getUser();

        if(userStorageSize<maxUserStorageSize-fileSize||user.getIsSystemRole()) {
            try (InputStream is = new ByteArrayInputStream(compressedData)) {
                System.out.println("SaveFile");
                Files.copy(is, filePath);

                CustomPhoto photo = new CustomPhoto();
                photo.setUserUIID(user.getUserUIID());
                photo.setPath(filePath.toString());
                photo.setSize(fileSize);

                user.getUserName();

                CustomPhoto savedPhoto = userService.saveUserPhoto(photo);
            }
        }else{
            throw new NoAvailableStorageForUser("No available storage for user. Please delete old files.");
        }


        return filePath.toString(); // Возвращаем относительный путь
    }

    class NoAvailableStorageForUser extends IOException{
        NoAvailableStorageForUser() {
        }
        NoAvailableStorageForUser(String msg) {
            super(msg);
        }
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


    private InputStream compressImage(MultipartFile file) throws IOException {
        InputStream inputStream = file.getInputStream();

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("Couldn't read the image");
        }
        int width = image.getWidth();
        int height = image.getHeight();

        // Если изображение уже меньше или равно целевому размеру — не меняем
        if (width <= targetWidth && height <= targetHeight && file.getSize()<=maxFileSizeBytes) {
            return file.getInputStream(); // Возвращаем исходный байт-массив
        }

        if(targetWidth>width){
            targetWidth=width;
        }
        if(targetHeight>height){
            targetHeight=height;
        }
        double quality = 1.0;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        while (true) {
            outputStream.reset(); // очищаем буфер

            System.out.println("File type " + file.getContentType());
            System.out.println("File extension " + extension);
            System.out.println("File targetHeight " + targetHeight);
            System.out.println("File targetWidth " + targetWidth);

            try {
                Thumbnails.of(inputStream)
                        .size(targetWidth, targetHeight)
                        .outputFormat(extension)
                        .outputQuality(quality)
                        .toOutputStream(outputStream);
            }
            catch (Exception e){
                System.out.println(e);
            }

            System.out.println("file size: " + outputStream.size());
            if (outputStream.size() <= maxFileSizeBytes) {
                return new ByteArrayInputStream(outputStream.toByteArray());
            }

            quality -= 0.1;
            targetHeight-=100;
            targetWidth-=100;

            if (quality < 0.1) {
                throw new IOException("Couldn't compress the file ");
            }

            inputStream = new ByteArrayInputStream(outputStream.toByteArray()); // обновляем InputStream
        }
    }
}