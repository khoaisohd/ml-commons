package org.opensearch.ml.engine.algorithms.oci;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.oci.OciClientAuthType;
import org.opensearch.ml.common.oci.OciClientUtils;

import java.io.FileInputStream;
import java.util.Locale;
import java.util.Map;

/**
 * OciAuthProviderFactory is responsible to generate authentication provider used to call OCI services
 */
@Log4j2
public class OciAuthProviderFactory {
    /**
     *
     * @param connectionParameters the client credentials
     * @return the authentication details provider which is used to call OCI services
     */
    public static BasicAuthenticationDetailsProvider buildAuthenticationDetailsProvider(
            final Map<String, String> connectionParameters) {
        OciClientUtils.validateConnectionParameters(connectionParameters);

        final OciClientAuthType authType =
                OciClientAuthType.from(
                        connectionParameters.get(
                                OciClientUtils.AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT));

        switch (authType) {
            case RESOURCE_PRINCIPAL:
                return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            case INSTANCE_PRINCIPAL:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case USER_PRINCIPAL:
                return SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(connectionParameters.get(OciClientUtils.TENANT_ID_FIELD))
                        .userId(connectionParameters.get(OciClientUtils.USER_ID_FIELD))
                        .region(Region.fromRegionCodeOrId(connectionParameters.get(OciClientUtils.REGION_FIELD)))
                        .fingerprint(connectionParameters.get(OciClientUtils.FINGERPRINT_FIELD))
                        .privateKeySupplier(
                                () -> {
                                    try {
                                        return new FileInputStream(connectionParameters.get(OciClientUtils.PEMFILE_PATH_FIELD));
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to read private key", e);
                                    }
                                })
                        .build();
            default:
                throw new IllegalArgumentException("OCI client auth type is not supported " + authType);
        }
    }}
