package org.my_ration.file_uploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
