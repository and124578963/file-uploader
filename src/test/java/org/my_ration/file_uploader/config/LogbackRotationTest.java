package org.my_ration.file_uploader.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Проверяет боевой logback.xml: имя файла и ротацию по размеру (лимит уменьшен до 1KB). */
class LogbackRotationTest {

    @TempDir
    Path logDir;

    @Test
    void writesToFileUploaderLogAndRotatesBySize() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("LOG_DIR", logDir.toString());
        context.putProperty("LOG_MAX_FILE_SIZE", "1KB");
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback.xml"));

        Logger log = context.getLogger("rotation-test");
        for (int i = 0; i < 200; i++) {
            log.info("line {} {}", i, "x".repeat(100));
        }

        String archivePrefix = "file-uploader-" + LocalDate.now() + ".";
        // Сжатие архивов идёт в фоне.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(archives(archivePrefix)).hasSizeGreaterThan(1));
        context.stop();

        assertThat(Files.readString(logDir.resolve("file-uploader.log"))).contains("line 199");
    }

    private File[] archives(String prefix) {
        return logDir.toFile().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log.gz"));
    }
}
