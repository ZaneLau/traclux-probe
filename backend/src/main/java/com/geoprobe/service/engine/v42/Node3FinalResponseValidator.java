package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.RawResponse;
import org.springframework.util.StringUtils;

final class Node3FinalResponseValidator {

    RawResponse validate(String providerCode, RawResponse response) {
        if (response == null) {
            throw new ProviderDispatchException("EMPTY_FINAL_RESPONSE", describeProvider(providerCode) + " 返回空响应");
        }
        if (!StringUtils.hasText(response.fullText())) {
            throw new ProviderDispatchException(
                    "EMPTY_FINAL_RESPONSE",
                    describeProvider(providerCode) + " 返回空正文，可能未产出最终答案",
                    response
            );
        }
        if (response.earlyStoppedFlag()) {
            throw new ProviderDispatchException(
                    "TRUNCATED_FINAL_RESPONSE",
                    describeProvider(providerCode) + " 响应因长度上限被截断，不能作为完整样本",
                    response
            );
        }
        return response;
    }

    private String describeProvider(String providerCode) {
        return StringUtils.hasText(providerCode) ? providerCode.trim() : "provider";
    }
}
