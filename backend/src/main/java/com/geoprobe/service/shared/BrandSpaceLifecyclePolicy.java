package com.geoprobe.service.shared;

import java.time.LocalDateTime;

public final class BrandSpaceLifecyclePolicy {

    public static final int SCOPE_STATUS_ACTIVE = 1;
    public static final int SCOPE_STATUS_ARCHIVED = 0;
    public static final int USER_SELF_RESTORE_DAYS = 30;
    public static final int INTERNAL_RESTORE_DAYS = 180;

    private BrandSpaceLifecyclePolicy() {
    }

    public static boolean isArchived(Integer scopeStatusCode) {
        return scopeStatusCode != null && scopeStatusCode == SCOPE_STATUS_ARCHIVED;
    }

    public static boolean isActiveOrLegacy(Integer scopeStatusCode) {
        return scopeStatusCode == null || scopeStatusCode == SCOPE_STATUS_ACTIVE;
    }

    public static LocalDateTime resolveRetentionUntil(LocalDateTime archivedAt) {
        if (archivedAt == null) {
            return null;
        }
        return archivedAt.plusDays(USER_SELF_RESTORE_DAYS);
    }

    public static LocalDateTime resolveInternalRestoreUntil(LocalDateTime archivedAt) {
        if (archivedAt == null) {
            return null;
        }
        return archivedAt.plusDays(INTERNAL_RESTORE_DAYS);
    }

    public static boolean isWindowOpen(LocalDateTime deadline, LocalDateTime now) {
        return deadline != null && now != null && !now.isAfter(deadline);
    }
}
