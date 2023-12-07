package org.opensearch.ml.common.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;

public class OciConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NullCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        OciConnector.ociConnectorBuilder().protocol(ConnectorProtocols.OCI_GENAI).build();
    }

    @Test
    public void constructor_NullAuthType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing auth type");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_GENAI)
                .credential(new HashMap<>())
                .build();
    }
}
