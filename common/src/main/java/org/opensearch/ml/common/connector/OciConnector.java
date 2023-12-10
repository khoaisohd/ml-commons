package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.oci.OciClientUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_GENAI;

/**
 * Connector to OCI Genai
 */
@Log4j2
@NoArgsConstructor
@EqualsAndHashCode
@org.opensearch.ml.common.annotation.Connector(OCI_GENAI)
public class OciConnector extends HttpConnector {
    @Builder(builderMethodName = "ociConnectorBuilder")
    public OciConnector(String name, String description, String version, String protocol,
                        Map<String, String> parameters, Map<String, String> credential, List<ConnectorAction> actions,
                        List<String> backendRoles, AccessMode accessMode, User owner) {
        super(name, description, version, protocol, parameters, credential, actions, backendRoles, accessMode, owner);
        validate();
    }

    public OciConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        validate();
    }


    public OciConnector(StreamInput input) throws IOException {
        super(input);
        validate();
    }

    private void validate() {
        OciClientUtils.validateConnectionParameters(parameters);
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new OciConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
