package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.oci.OciClientAuthConfig;
import org.opensearch.ml.common.oci.OciClientAuthType;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;

/**
 * Connector to OCI Genai
 */
@Log4j2
@NoArgsConstructor
@EqualsAndHashCode
@org.opensearch.ml.common.annotation.Connector(OCI_SIGV1)
public class OciConnector extends HttpConnector {
    public static final String AUTH_TYPE_FIELD = "auth_type";

    public static final String TENANT_ID_FIELD = "tenant_id";

    public static final String USER_ID_FIELD = "user_id";

    public static final String FINGERPRINT_FIELD = "fingerprint";

    public static final String PEMFILE_PATH_FIELD = "pemfile_path";

    public static final String REGION_FIELD = "region";

    @Getter
    private OciClientAuthConfig ociClientAuthConfig;

    @Builder(builderMethodName = "ociConnectorBuilder")
    public OciConnector(String name, String description, String version, String protocol,
                        Map<String, String> parameters, Map<String, String> credential, List<ConnectorAction> actions,
                        List<String> backendRoles, AccessMode accessMode, User owner) {
        super(name, description, version, protocol, parameters, credential, actions, backendRoles, accessMode, owner);
        initOciClientAuthConfigAndValidate();
    }

    public OciConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        initOciClientAuthConfigAndValidate();
    }


    public OciConnector(StreamInput input) throws IOException {
        super(input);
        initOciClientAuthConfigAndValidate();
    }

    private void initOciClientAuthConfigAndValidate() {
        if (!parameters.containsKey(AUTH_TYPE_FIELD)) {
            throw new IllegalArgumentException("Missing auth type");
        }

        this.ociClientAuthConfig = new OciClientAuthConfig(
                OciClientAuthType.from(
                        parameters.get(AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT)),
                parameters.get(TENANT_ID_FIELD),
                parameters.get(USER_ID_FIELD),
                parameters.get(REGION_FIELD),
                parameters.get(FINGERPRINT_FIELD),
                parameters.get(PEMFILE_PATH_FIELD));
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            final StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new OciConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
