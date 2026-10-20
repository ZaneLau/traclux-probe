package com.geoprobe.service.shared;

import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class V42TaskOrderExecutionPolicyResolver {

    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy;
    private final V42RealtimeSearchPolicyResolver v42RealtimeSearchPolicyResolver;

    public V42TaskOrderExecutionPolicy defaultPolicy() {
        return new V42TaskOrderExecutionPolicy(
                v42TargetProbeCountPolicy.defaultTargetProbeCount(),
                v42RealtimeSearchPolicyResolver.defaultPolicy()
        );
    }

    public V42TaskOrderExecutionPolicy resolveForFrontDeskPlanning(Map<String, Object> taskOrderHints) {
        return new V42TaskOrderExecutionPolicy(
                v42TargetProbeCountPolicy.resolveOrDefaultForFrontDeskPlanning(taskOrderHints),
                v42RealtimeSearchPolicyResolver.resolveFromTaskOrderHints(taskOrderHints)
        );
    }

    public V42TaskOrderExecutionPolicy resolveRequiredFromTaskOrderHints(Map<String, Object> taskOrderHints,
                                                                         String scene) {
        return new V42TaskOrderExecutionPolicy(
                v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(taskOrderHints, scene),
                v42RealtimeSearchPolicyResolver.resolveFromTaskOrderHints(taskOrderHints)
        );
    }

    public V42TaskOrderExecutionPolicy resolveRequiredFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot,
                                                                            String scene) {
        int targetProbeCount = v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, scene);
        RealtimeSearchPolicy realtimeSearchPolicy =
                v42RealtimeSearchPolicyResolver.resolveFromTaskOrderSnapshot(taskOrderSnapshot);
        return new V42TaskOrderExecutionPolicy(targetProbeCount, realtimeSearchPolicy);
    }

    public RealtimeSearchPolicy resolveRealtimeSearchPolicyFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot) {
        return v42RealtimeSearchPolicyResolver.resolveFromTaskOrderSnapshot(taskOrderSnapshot);
    }

    public Map<String, Object> normalizeTaskOrderHints(Map<String, Object> taskOrderHints,
                                                       V42TaskOrderExecutionPolicy policy) {
        Map<String, Object> normalizedTargetHints =
                v42TargetProbeCountPolicy.normalizeTaskOrderHints(taskOrderHints, policy.targetProbeCount());
        return v42RealtimeSearchPolicyResolver.normalizeTaskOrderHints(
                normalizedTargetHints,
                policy.realtimeSearchPolicy()
        );
    }

    public String describeScene(String scene) {
        return StringUtils.hasText(scene) ? scene.trim() : "unknown";
    }
}
