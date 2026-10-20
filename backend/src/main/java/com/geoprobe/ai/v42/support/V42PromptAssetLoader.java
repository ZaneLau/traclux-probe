package com.geoprobe.ai.v42.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class V42PromptAssetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final V42SecretAssetSettings settings;
    private final Map<V42PromptAssetKey, LoadedAsset> cache = new ConcurrentHashMap<>();
    private final Map<String, LoadedAsset> versionCache = new ConcurrentHashMap<>();

    public V42PromptAssetLoader(V42SecretAssetSettings settings) {
        this.settings = settings;
    }

    public V42PromptAssetDocument load(V42PromptAssetKey key) {
        return cache.computeIfAbsent(key, this::readCurrentAssetUnchecked).document();
    }

    public V42PromptAssetDescriptor describe(V42PromptAssetKey key) {
        return cache.computeIfAbsent(key, this::readCurrentAssetUnchecked).descriptor();
    }

    public V42PromptAssetDescriptor describeVersion(V42PromptAssetKey key, String version) {
        if (!StringUtils.hasText(version)) {
            throw new IllegalArgumentException("prompt 资产 version 不能为空");
        }
        LoadedAsset current = cache.computeIfAbsent(key, this::readCurrentAssetUnchecked);
        if (version.trim().equals(current.document().version())) {
            return current.descriptor();
        }
        return versionCache.computeIfAbsent(versionCacheKey(key, version.trim()), ignored -> readVersionedAssetUnchecked(key, version.trim()))
                .descriptor();
    }

    public V42PromptAssetDocument loadVersion(V42PromptAssetKey key, String version) {
        if (!StringUtils.hasText(version)) {
            throw new IllegalArgumentException("prompt 资产 version 不能为空");
        }
        LoadedAsset current = cache.computeIfAbsent(key, this::readCurrentAssetUnchecked);
        if (version.trim().equals(current.document().version())) {
            return current.document();
        }
        return versionCache.computeIfAbsent(versionCacheKey(key, version.trim()), ignored -> readVersionedAssetUnchecked(key, version.trim()))
                .document();
    }

    public void verifyAll() {
        for (V42PromptAssetKey key : V42PromptAssetKey.values()) {
            load(key);
        }
    }

    private LoadedAsset readCurrentAssetUnchecked(V42PromptAssetKey key) {
        try {
            Resource currentResource = resolveCurrentResource(key);
            if (currentResource != null && currentResource.exists()) {
                return readLoadedAsset(key, currentResource);
            }
            return readLoadedAsset(key, resolveLegacyResource(key));
        } catch (IOException ex) {
            throw new IllegalStateException("V4.2 prompt 资产读取失败: " + key.name(), ex);
        }
    }

    private LoadedAsset readVersionedAssetUnchecked(V42PromptAssetKey key, String version) {
        try {
            return readLoadedAsset(key, resolveVersionedResource(key, version));
        } catch (IOException ex) {
            throw new IllegalStateException("V4.2 prompt 历史资产读取失败: " + key.name(), ex);
        }
    }

    private V42PromptAssetDocument readAssetUnchecked(V42PromptAssetKey key, Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            V42PromptAssetDocument document = objectMapper.readValue(inputStream, V42PromptAssetDocument.class);
            validateDocument(key, document);
            return document;
        } catch (IOException ex) {
            throw new IllegalStateException("V4.2 prompt 资产读取失败: " + key.name(), ex);
        }
    }

    private LoadedAsset readLoadedAsset(V42PromptAssetKey key, Resource resource) throws IOException {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("V4.2 prompt 资产不存在: " + (resource == null ? key.name() : resource.getDescription()));
        }
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            V42PromptAssetDocument document = objectMapper.readValue(bytes, V42PromptAssetDocument.class);
            validateDocument(key, document);
            V42PromptAssetDescriptor descriptor = new V42PromptAssetDescriptor(
                    key,
                    document.version(),
                    resource.getDescription(),
                    sha256Hex(new String(bytes, StandardCharsets.UTF_8))
            );
            log.info("V4.2 secret asset loaded. key={}, version={}, source={}",
                    key.name(), document.version(), resource.getDescription());
            return new LoadedAsset(document, descriptor);
        }
    }

    private Resource resolveCurrentResource(V42PromptAssetKey key) {
        return resourceLoader.getResource(normalizeLocation(settings.promptAssetDir()) + "/" + key.assetPath() + "/current.json");
    }

    private Resource resolveVersionedResource(V42PromptAssetKey key, String version) {
        Resource resource = resourceLoader.getResource(
                normalizeLocation(settings.promptAssetDir()) + "/" + key.assetPath() + "/versions/" + version + ".json"
        );
        if (!resource.exists()) {
            throw new IllegalStateException("V4.2 prompt 历史资产不存在: " + resource.getDescription());
        }
        return resource;
    }

    private Resource resolveLegacyResource(V42PromptAssetKey key) {
        return resourceLoader.getResource(normalizeLocation(settings.promptAssetDir()) + "/" + key.fileName());
    }

    private String normalizeLocation(String root) {
        String normalizedRoot = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String location = normalizedRoot;
        if (!location.contains(":")) {
            location = "file:" + location;
        }
        return location;
    }

    private void validateDocument(V42PromptAssetKey key, V42PromptAssetDocument document) {
        if (document == null) {
            throw new IllegalStateException("V4.2 prompt 资产为空: " + key.name());
        }
        if (!StringUtils.hasText(document.version())) {
            throw new IllegalStateException("V4.2 prompt 资产缺少 version: " + key.name());
        }
        if (!StringUtils.hasText(document.systemMessage())) {
            throw new IllegalStateException("V4.2 prompt 资产缺少 systemMessage: " + key.name());
        }
        if (!StringUtils.hasText(document.userMessage())) {
            throw new IllegalStateException("V4.2 prompt 资产缺少 userMessage: " + key.name());
        }
        if (document.fewShotExamples() != null) {
            for (V42PromptAssetExample example : document.fewShotExamples()) {
                if (example == null
                        || !StringUtils.hasText(example.userMessage())
                        || !StringUtils.hasText(example.assistantMessage())) {
                    throw new IllegalStateException("V4.2 prompt 资产 few-shot 非法: " + key.name());
                }
            }
        }
    }

    private String versionCacheKey(V42PromptAssetKey key, String version) {
        return key.name() + "::" + version;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record LoadedAsset(
            V42PromptAssetDocument document,
            V42PromptAssetDescriptor descriptor
    ) {
    }
}
