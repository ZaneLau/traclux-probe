package com.geoprobe.service.shared;

import java.util.List;
import java.util.Map;

public record DiagnosisAliasConfigData(
        List<String> brandAliases,
        Map<String, List<String>> competitorAliases
) {
}
