package org.opensearch.ml.engine.algorithms.oci;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.oci.OciClientAuthConfig;

import java.io.FileInputStream;

/**
 * OciAuthProviderFactory is responsible to generate authentication provider used to call OCI services
 */
@Log4j2
public class OciAuthProviderFactory {
    /**
     *
     * @param ociClientAuthConfig the client auth config
     * @return the authentication details provider which is used to call OCI services
     */
    public static BasicAuthenticationDetailsProvider buildAuthenticationDetailsProvider(
            final OciClientAuthConfig ociClientAuthConfig) {
        switch (ociClientAuthConfig.getAuthType()) {
            case RESOURCE_PRINCIPAL:
                return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            case INSTANCE_PRINCIPAL:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case USER_PRINCIPAL:
                return SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(ociClientAuthConfig.getTenantId())
                        .userId(ociClientAuthConfig.getUserId())
                        .region(Region.fromRegionCodeOrId(ociClientAuthConfig.getRegion()))
                        .fingerprint(ociClientAuthConfig.getFingerprint())
                        .privateKeySupplier(
                                () -> {
                                    try {
                                        return new FileInputStream(ociClientAuthConfig.getPemfilepath());
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to read private key", e);
                                    }
                                })
                        .build();
            default:
                throw new IllegalArgumentException("OCI client auth type is not supported " + ociClientAuthConfig.getAuthType());
        }
    }}
