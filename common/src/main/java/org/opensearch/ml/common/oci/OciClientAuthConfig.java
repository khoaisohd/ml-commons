package org.opensearch.ml.common.oci;

import lombok.Getter;

/**
 * The auth configuration for OCI client
 */
@Getter
public class OciClientAuthConfig {
    /**
     * The client auth type
     */
    private final OciClientAuthType authType;

    /**
     * The tenancy id required for user principal
     */
    private final String tenantId;

    /**
     * The user id required for user principal
     */
    private final String userId;

    /**
     * The region required for user principal
     */
    private final String region;

    /**
     * The fingerprint required for user principal
     */
    private final String fingerprint;

    /**
     * The pemfilepath required for user principal
     */
    private final String pemfilepath;

    /**
     *
     * @param authType the client auth type
     * @param tenantId the tenant id
     * @param userId the user id
     * @param region the OCI region
     * @param fingerprint the fingerprint
     * @param pemfilepath the path of pem file
     */
    public OciClientAuthConfig(
            final OciClientAuthType authType,
            final String tenantId,
            final String userId,
            final String region,
            final String fingerprint,
            final String pemfilepath) {
        if (authType == null) {
            throw new IllegalArgumentException("Missing auth type");
        }

        if (authType == OciClientAuthType.USER_PRINCIPAL) {
            if (tenantId == null) {
                throw new IllegalArgumentException("Missing tenant id");
            }

            if (userId == null) {
                throw new IllegalArgumentException("Missing user id");
            }

            if (fingerprint == null) {
                throw new IllegalArgumentException("Missing fingerprint");
            }

            if (pemfilepath == null) {
                throw new IllegalArgumentException("Missing pemfile");
            }

            if (region == null) {
                throw new IllegalArgumentException("Missing region");
            }
        }

        this.authType = authType;
        this.tenantId = tenantId;
        this.userId = userId;
        this.region = region;
        this.fingerprint = fingerprint;
        this.pemfilepath = pemfilepath;
    }
}
