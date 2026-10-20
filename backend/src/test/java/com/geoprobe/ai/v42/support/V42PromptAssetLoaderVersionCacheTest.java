package com.geoprobe.ai.v42.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V42PromptAssetLoaderVersionCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReuseCachedHistoricalVersionAfterSourceFileRemoved() throws IOException {
        Path source = Path.of("src/test/resources/v42-test-prompt-assets-versioned");
        copyTree(source, tempDir);
        V42PromptAssetLoader loader = new V42PromptAssetLoader(new V42SecretAssetSettings(tempDir.toAbsolutePath().toString(), true));

        V42PromptAssetDocument first = loader.loadVersion(V42PromptAssetKey.PROBE_RENDERING, "node2-v2026-05-01");
        Files.delete(tempDir.resolve("node2/probe_rendering/versions/node2-v2026-05-01.json"));
        V42PromptAssetDocument second = loader.loadVersion(V42PromptAssetKey.PROBE_RENDERING, "node2-v2026-05-01");

        assertEquals("node2-v2026-05-01", first.version());
        assertEquals(first.version(), second.version());
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }
}
