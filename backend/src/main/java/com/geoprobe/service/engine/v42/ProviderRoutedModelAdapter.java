package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ProviderRoutedModelAdapter implements ModelAdapter {

    private final Map<String, ModelAdapter> delegates;

    public ProviderRoutedModelAdapter(Map<String, ModelAdapter> delegates) {
        this.delegates = Map.copyOf(Objects.requireNonNull(delegates, "delegates 不能为空"));
    }

    @Override
    public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
        Objects.requireNonNull(dispatchControl, "dispatchControl 不能为空");
        String provider = normalizeProvider(dispatchControl.targetProvider(), dispatchControl.targetModelId());
        ModelAdapter delegate = delegates.get(provider);
        if (delegate == null) {
            throw new ProviderDispatchException("TARGET_PROVIDER_UNSUPPORTED", "当前未接入 GE provider: " + provider);
        }
        return delegate.sendProbe(probeInput, dispatchControl);
    }

    private String normalizeProvider(String provider, String modelId) {
        if (StringUtils.hasText(provider)) {
            return provider.trim().toLowerCase(Locale.ROOT);
        }
        String inferred = Node3TargetEnginePolicy.inferProviderFromModelId(modelId);
        if (StringUtils.hasText(inferred)) {
            return inferred;
        }
        throw new ProviderDispatchException("TARGET_PROVIDER_UNSPECIFIED", "缺少可解析的 GE provider");
    }
}
