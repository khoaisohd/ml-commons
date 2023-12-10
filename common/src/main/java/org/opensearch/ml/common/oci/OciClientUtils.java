package org.opensearch.ml.common.oci;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.Locale;
import java.util.Map;

/**
 * Set of utilization for OCI client
 */
@UtilityClass
@Log4j2
public class OciClientUtils {
    public final String AUTH_TYPE_FIELD = "auth_type";
    public final String TENANT_ID_FIELD = "tenant_id";
    public final String USER_ID_FIELD = "user_id";
    public final String FINGERPRINT_FIELD = "fingerprint";
    public final String PEMFILE_PATH_FIELD = "pemfile_path";
    public final String REGION_FIELD = "region";

    /**
     * Validate credential used to build OCI client
     * @param connectionParameters the OCI Client credential
     */
    public void validateConnectionParameters(final Map<String, String> connectionParameters) {
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
