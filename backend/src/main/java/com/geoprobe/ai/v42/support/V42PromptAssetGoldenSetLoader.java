package com.geoprobe.ai.v42.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class V42PromptAssetGoldenSetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    public List<V42PromptGoldenSetCase> load(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("prompt golden set 路径不能为空");
        }
        Resource resource = resourceLoader.getResource(location.trim());
        if (!resource.exists()) {
            throw new IllegalStateException("prompt golden set 不存在: " + resource.getDescription());
        }
        try (InputStream inputStream = resource.getInputStream()) {
            List<V42PromptGoldenSetCase> cases = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<V42PromptGoldenSetCase>>() {
                    }
            );
            if (cases == null || cases.isEmpty()) {
                throw new IllegalStateException("prompt golden set 不能为空: " + resource.getDescription());
            }
            return List.copyOf(cases);
        } catch (IOException ex) {
            throw new IllegalStateException("prompt golden set 读取失败: " + resource.getDescription(), ex);
        }
    }
}
