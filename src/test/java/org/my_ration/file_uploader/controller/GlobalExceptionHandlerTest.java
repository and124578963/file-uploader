package org.my_ration.file_uploader.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.my_ration.file_uploader.service.FileStorageService;
import org.my_ration.file_uploader.service.StorageQuotaExceededException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FileUploadController(storage))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void storeThrows(Throwable t) throws Exception {
        when(storage.storeFile(any(), anyString())).thenThrow(t);
    }

    private org.springframework.test.web.servlet.ResultActions upload() throws Exception {
        return mvc.perform(multipart("/api/upload")
                .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}))
                .param("category", "recipes"));
    }

    @Test
    void validationErrorIs400WithCode() throws Exception {
        storeThrows(new IllegalArgumentException("invalid_file_type"));

        upload().andExpect(status().isBadRequest()).andExpect(content().string("invalid_file_type"));
    }

    @Test
    void storageQuotaIs507WithCode() throws Exception {
        storeThrows(new StorageQuotaExceededException(5_200_000, 197_361, 5_242_880));

        upload().andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.code").value("no_available_storage"))
                .andExpect(jsonPath("$.usedBytes").value(5_200_000))
                .andExpect(jsonPath("$.fileSizeBytes").value(197_361))
                .andExpect(jsonPath("$.limitBytes").value(5_242_880));
    }

    @Test
    void nonIoExceptionWithoutMessageIs500() throws Exception {
        storeThrows(new IllegalStateException());

        upload().andExpect(status().isInternalServerError());
    }

    @Test
    void outOfMemoryIs500() throws Exception {
        storeThrows(new OutOfMemoryError("Java heap space"));

        upload().andExpect(status().isInternalServerError());
    }

    @Test
    void tooLargeUploadIs413() throws Exception {
        storeThrows(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        upload().andExpect(status().isPayloadTooLarge()).andExpect(content().string("file_too_large"));
    }
}
