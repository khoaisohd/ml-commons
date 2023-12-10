package org.opensearch.ml.common.oci;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

public class OciClientUtilsTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validateCredential_NullCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        OciClientUtils.validateConnectionParameters(null);
    }

    @Test
    public void validateCredential_MissingAuthType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing auth type");
        OciClientUtils.validateConnectionParameters(Map.of());
    }

    @Test
    public void validateCredential_MissingTenantIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing tenant id");
        OciClientUtils.validateConnectionParameters(
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, "USER_PRINCIPAL"));
    }

    @Test
    public void validateCredential_MissingUserIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing user id");
        OciClientUtils.validateConnectionParameters(
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                        OciClientUtils.TENANT_ID_FIELD, "tenantId"));
    }

    @Test
    public void validateCredential_MissingFingerprintForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing fingerprint");
        OciClientUtils.validateConnectionParameters(
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                        OciClientUtils.TENANT_ID_FIELD, "tenantId",
                        OciClientUtils.USER_ID_FIELD, "userId"));
    }

    @Test
    public void validateCredential_MissingPemfileForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing pemfile");
        OciClientUtils.validateConnectionParameters(
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                        OciClientUtils.TENANT_ID_FIELD, "tenantId",
                        OciClientUtils.USER_ID_FIELD, "userId",
                        OciClientUtils.FINGERPRINT_FIELD, "fingerprint"));
    }

    @Test
    public void validateCredential_MissingRegionForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing region");
        OciClientUtils.validateConnectionParameters(
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                        OciClientUtils.TENANT_ID_FIELD, "tenantId",
                        OciClientUtils.USER_ID_FIELD, "userId",
                        OciClientUtils.FINGERPRINT_FIELD, "fingerprint",
                        OciClientUtils.PEMFILE_PATH_FIELD, "pemfile"));
    }
}
