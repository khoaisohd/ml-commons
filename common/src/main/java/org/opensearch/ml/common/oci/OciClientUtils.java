package org.opensearch.ml.common.oci;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

/**
 * Set of utilization for OCI client
 */
@UtilityClass
@Log4j2
public class OciClientUtils {
    public static final String AUTH_TYPE_FIELD = "auth_type";
    public static final String TENANT_ID_FIELD = "tenant_id";
    public static final String USER_ID_FIELD = "user_id";
    public static final String FINGERPRINT_FIELD = "fingerprint";
    public static final String PEMFILE_PATH_FIELD = "pemfile_path";
    public static final String REGION_FIELD = "region";

    /**
     * Validate credential used to build OCI client
     * @param credential the OCI Client credential
     */
    public void validateCredential(final Map<String, String> credential) {
        if (credential == null) {
            throw new IllegalArgumentException("Missing credential");
        }
        if (!credential.containsKey(AUTH_TYPE_FIELD)) {
            throw new IllegalArgumentException("Missing auth type");
        }

        if (credential.get(AUTH_TYPE_FIELD).equals(OciClientAuthType.USER_PRINCIPAL.name())) {
            if (!credential.containsKey(TENANT_ID_FIELD)) {
                throw new IllegalArgumentException("Missing tenant id");
            }

            if (!credential.containsKey(USER_ID_FIELD)) {
                throw new IllegalArgumentException("Missing user id");
            }

            if (!credential.containsKey(FINGERPRINT_FIELD)) {
                throw new IllegalArgumentException("Missing fingerprint");
            }

            if (!credential.containsKey(PEMFILE_PATH_FIELD)) {
                throw new IllegalArgumentException("Missing pemfile");
            }

            if (!credential.containsKey(REGION_FIELD)) {
                throw new IllegalArgumentException("Missing region");
            }
        }
    }
}
