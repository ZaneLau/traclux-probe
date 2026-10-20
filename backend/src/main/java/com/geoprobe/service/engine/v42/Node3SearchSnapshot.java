package com.geoprobe.service.engine.v42;

import java.util.List;

record Node3SearchSnapshot(int requestedProbeCount,
                           int satisfiedProbeCount,
                           double evidenceSuccessRate,
                           int fallbackProbeCount,
                           boolean qualityGatePassed,
                           List<String> unsatisfiedProbeIds) {
}
