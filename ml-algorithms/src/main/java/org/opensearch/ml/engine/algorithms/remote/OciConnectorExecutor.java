/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.internal.RestClient;
import com.oracle.bmc.http.internal.RestClientFactory;
import com.oracle.bmc.http.internal.RestClientFactoryBuilder;
import com.oracle.bmc.http.internal.WrappedInvocationBuilder;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.requests.BmcRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.engine.algorithms.oci.OciAuthProviderFactory;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;
import javax.ws.rs.client.WebTarget;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;

/**
 * OciConnectorExecutor is responsible to call remote model from OCI services
 */
@Log4j2
@ConnectorExecutor(OCI_SIGV1)
public class OciConnectorExecutor implements RemoteConnectorExecutor{

    @Getter
    private final OciConnector connector;

    @Setter @Getter
    private ScriptService scriptService;

    private final RestClient restClient;

    public OciConnectorExecutor(Connector connector) {
        this.connector = (OciConnector) connector;

        final BasicAuthenticationDetailsProvider provider =
                OciAuthProviderFactory.buildAuthenticationDetailsProvider(connector.getParameters());
        final RestClientFactory restClientFactory = RestClientFactoryBuilder.builder().build();

        RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);
        this.restClient = restClientFactory.create(requestSigner, Collections.emptyMap());
    }


     @Override
     public Response executeRemoteCall(String endpoint, String httpMethod, String payload) {
         final WebTarget target = getWebTarget(endpoint);
         final WrappedInvocationBuilder wrappedIb = new WrappedInvocationBuilder(target.request(), target.getUri());
         final javax.ws.rs.core.Response response;
         switch (httpMethod.toUpperCase(Locale.ROOT)) {
             case "POST":
                 response = restClient.post(wrappedIb, payload, new BmcRequest<>());
                 break;
             case "GET":
                 response = restClient.get(wrappedIb, new BmcRequest<>());
                 break;
             default:
                 throw new IllegalArgumentException("unsupported http method");
         }
         return new Response((InputStream) response.getEntity(), response.getStatus());
    }

    @Synchronized
    private WebTarget getWebTarget(String endpoint) {
        restClient.setEndpoint(endpoint);
        return restClient.getBaseTarget();
    }
}
