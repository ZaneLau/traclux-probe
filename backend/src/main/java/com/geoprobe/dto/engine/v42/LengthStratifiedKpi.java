package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record LengthStratifiedKpi(
        LengthBucketKpi shortBucket,
        LengthBucketKpi mediumBucket,
        LengthBucketKpi longBucket
) {

    public LengthStratifiedKpi {
        shortBucket = Objects.requireNonNull(shortBucket, "shortBucket 不能为空");
        mediumBucket = Objects.requireNonNull(mediumBucket, "mediumBucket 不能为空");
        longBucket = Objects.requireNonNull(longBucket, "longBucket 不能为空");
    }
}
