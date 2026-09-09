package com.fras.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Handles OpenCV model resources.
 *
 * Models are stored inside src/main/resources/models
 * and extracted to a temporary file when required.
 */
public final class OpenCVConfig {

    private OpenCVConfig() {
    }

    public static String resolveResourceToFile(
            String resourcePath,
            String prefix,
            String suffix
    ) {

        try (
                InputStream input =
                        OpenCVConfig.class
                                .getResourceAsStream(
                                        resourcePath
                                )
        ) {

            if (input == null) {

                throw new IllegalStateException(
                        "OpenCV model resource not found: "
                                + resourcePath
                );
            }

            Path tempFile =
                    Files.createTempFile(
                            prefix,
                            suffix
                    );

            Files.copy(
                    input,
                    tempFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            tempFile
                    .toFile()
                    .deleteOnExit();

            return tempFile
                    .toAbsolutePath()
                    .toString();

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Unable to extract OpenCV model: "
                            + resourcePath,
                    e
            );
        }
    }
}