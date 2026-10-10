package com.hic.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntityAuditListenerTest {
    private final EntityAuditListener listener = new EntityAuditListener();

    @Test void identifiesOnlyChangedFields() {
        var result = listener.changes("Employee", new String[]{"firstName", "branchId", "salary", "updatedAt"},
                new Object[]{"Test", 1L, 10, "yesterday"}, new Object[]{"Test", 2L, 20, "today"});
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("field", "branchId").containsEntry("before", "1").containsEntry("after", "2");
    }
    @Test void neverCopiesSecretsOrBiometricBytes() {
        var result = listener.changes("User", new String[]{"passwordHash", "email"},
                new Object[]{"OLD-SECRET", "old@example.test"}, new Object[]{"NEW-SECRET", "new@example.test"});
        assertThat(result.toString()).doesNotContain("SECRET", "example.test");
        assertThat(result).hasSize(2);
        assertThat(listener.changes("FaceData", new String[]{"faceImage", "faceImageUrl"}, null,
                new Object[]{new byte[]{1,2,3}, "/secret/photo/path"}).toString()).doesNotContain("secret/photo/path");
    }
    @Test void healthPollingDoesNotFloodJournal() {
        assertThat(listener.changes("DeviceConfig", new String[]{"online", "lastSyncTime"},
                new Object[]{false, "old"}, new Object[]{true, "new"})).isEmpty();
    }
}
