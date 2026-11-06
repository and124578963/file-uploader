package org.my_ration.file_uploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new FileStorageService(null, null);
        ReflectionTestUtils.setField(service, "rootDirectory", root.toString());
        ReflectionTestUtils.setField(service, "allowedMimeTypes", new String[]{"image/png"});
        ReflectionTestUtils.setField(service, "allowedCategories", new String[]{"recipes", "userIngredients"});
        Files.createDirectories(root.resolve("recipes"));
        // Каталог существует, но в белый список не входит.
        Files.createDirectories(root.resolve("logs"));
    }

    @Test
    void extensionComesFromDetectedFormatNotFromUploadedName() {
        String name = FileStorageService.storedFileName("evil.html", "jpg");

        assertThat(name).endsWith(".jpg").doesNotContain(".html");
    }

    @Test
    void originalBaseNameIsKeptWithoutDots() {
        String name = FileStorageService.storedFileName("my.photo.v2.PNG", "png");

        assertThat(name).matches("[0-9a-f-]{36}_my_photo_v2\\.png");
    }

    @Test
    void missingOriginalNameIsFine() {
        String name = FileStorageService.storedFileName(null, "png");

        assertThat(name).matches("[0-9a-f-]{36}\\.png");
    }

    @Test
    void longOriginalNameIsTruncated() {
        String name = FileStorageService.storedFileName("a".repeat(500) + ".jpg", "jpg");

        assertThat(name.length()).isLessThan(150);
    }

    @Test
    void parentDirectoryCategoryIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.storeFile(file, ".."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no_such_path");
    }

    @Test
    void existingDirectoryOutsideWhitelistIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.storeFile(file, "logs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no_such_path");
    }

    @Test
    void regularUserOverQuotaGetsQuotaException() throws Exception {
        PhotoService photos = quotaService(false, 999);

        assertThatThrownBy(() -> service.storeFile(png(), "recipes"))
                .isInstanceOf(StorageQuotaExceededException.class)
                .hasMessage("no_available_storage")
                .satisfies(e -> {
                    StorageQuotaExceededException q = (StorageQuotaExceededException) e;
                    assertThat(q.getUsedBytes()).isEqualTo(999);
                    assertThat(q.getLimitBytes()).isEqualTo(1000);
                });
        verify(photos, never()).saveUserPhoto(any());
    }

    @Test
    void adminIsNotLimitedByQuota() throws Exception {
        PhotoService photos = quotaService(true, 999);

        String path = service.storeFile(png(), "recipes");

        assertThat(Path.of(path)).exists();
        verify(photos).saveUserPhoto(any());
    }

    private PhotoService quotaService(boolean admin, long used) {
        User user = new User();
        user.setUserUIID("u1");
        user.setAdmin(admin);
        UserService users = mock(UserService.class);
        when(users.getUser()).thenReturn(user);
        PhotoService photos = mock(PhotoService.class);
        when(photos.getUserCurrentStorageSizeByUIID("u1")).thenReturn(used);

        service = new FileStorageService(users, photos);
        ReflectionTestUtils.setField(service, "rootDirectory", root.toString());
        ReflectionTestUtils.setField(service, "allowedMimeTypes", new String[]{"image/png"});
        ReflectionTestUtils.setField(service, "allowedCategories", new String[]{"recipes"});
        ReflectionTestUtils.setField(service, "targetWidth", 1024);
        ReflectionTestUtils.setField(service, "targetHeight", 1024);
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 524288L);
        ReflectionTestUtils.setField(service, "maxPixels", 100_000_000L);
        ReflectionTestUtils.setField(service, "maxUserStorageSize", 1000L);
        service.initializeWebPSupport();
        return photos;
    }

    private static MockMultipartFile png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "x.png", "image/png", out.toByteArray());
    }
}
