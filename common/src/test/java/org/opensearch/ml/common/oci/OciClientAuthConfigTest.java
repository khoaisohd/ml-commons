package org.opensearch.ml.common.oci;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

public class OciClientAuthConfigTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validateCredential_MissingAuthType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing auth type");
        new OciClientAuthConfig(
                null, "tenantId", "userId", "region", "fingerprint", "pemfilepath");
    }

    @Test
    public void validateCredential_MissingTenantIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing tenant id");
        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, null, "userId", "region", "fingerprint", "pemfilepath");
    }

    @Test
    public void validateCredential_MissingUserIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing user id");
        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, "tenantId", "userId", "region", "fingerprint", "pemfilepath");

        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, "tenantId", null, "region", "fingerprint", "pemfilepath");

    }

    @Test
    public void validateCredential_MissingFingerprintForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing fingerprint");
        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, "tenantId", "userId", "region", null, "pemfilepath");

    }

    @Test
    public void validateCredential_MissingPemfileForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing pemfile");
        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, "tenantId", "userId", "region", "fingerprint", null);

    }

    @Test
    public void validateCredential_MissingRegionForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing region");
        new OciClientAuthConfig(
                OciClientAuthType.USER_PRINCIPAL, "tenantId", "userId", null, "fingerprint", "pemfilepath");

    }
}
