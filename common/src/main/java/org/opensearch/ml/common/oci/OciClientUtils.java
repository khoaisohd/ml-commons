package org.opensearch.ml.common.oci;

import lombok.extern.log4j.Log4j2;

import java.util.Locale;
import java.util.Map;

/**
 * Set of utilization for OCI client
 */
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
     * @param connectionParameters the OCI Client credential
     */
    public static void validateConnectionParameters(final Map<String, String> connectionParameters) {
        if (connectionParameters == null) {
            throw new IllegalArgumentException("Missing credential");
        }
        if (!connectionParameters.containsKey(AUTH_TYPE_FIELD)) {
            throw new IllegalArgumentException("Missing auth type");
        }

        final OciClientAuthType authType =
                OciClientAuthType.from(
                        connectionParameters.get(
                                OciClientUtils.AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT));

        if (authType == OciClientAuthType.USER_PRINCIPAL) {
            if (!connectionParameters.containsKey(TENANT_ID_FIELD)) {
                throw new IllegalArgumentException("Missing tenant id");
            }

            if (!connectionParameters.containsKey(USER_ID_FIELD)) {
                throw new IllegalArgumentException("Missing user id");
            }

            if (!connectionParameters.containsKey(FINGERPRINT_FIELD)) {
                throw new IllegalArgumentException("Missing fingerprint");
            }

            if (!connectionParameters.containsKey(PEMFILE_PATH_FIELD)) {
                throw new IllegalArgumentException("Missing pemfile");
            }

            if (!connectionParameters.containsKey(REGION_FIELD)) {
                throw new IllegalArgumentException("Missing region");
            }
        }
    }
}
