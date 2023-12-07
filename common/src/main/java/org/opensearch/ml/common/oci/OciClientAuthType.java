package org.opensearch.ml.common.oci;

/**
 * The type of authentication supported by OCI. For more details please visit doc
 * https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/API/Concepts/sdk_authentication_methods.htm
 */
public enum OciClientAuthType {
    RESOURCE_PRINCIPAL,
    INSTANCE_PRINCIPAL,
    USER_PRINCIPAL;

    public static OciClientAuthType from(String value) {
        try {
            return OciClientAuthType.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong OCI client auth type");
        }
    }
}