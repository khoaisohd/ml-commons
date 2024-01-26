/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.http.internal.RestClient;
import com.oracle.bmc.http.internal.RestClientFactory;
import com.oracle.bmc.http.internal.RestClientFactoryBuilder;
import com.oracle.bmc.http.internal.WrappedInvocationBuilder;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.requests.BmcRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;
import static org.opensearch.ml.common.connector.OciConnector.OciClientAuthType;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

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

        final BasicAuthenticationDetailsProvider provider = buildAuthenticationDetailsProvider(connector);
        final RestClientFactory restClientFactory = RestClientFactoryBuilder.builder().build();
        final RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);
        this.restClient = restClientFactory.create(requestSigner, Collections.emptyMap());
    }

    @SneakyThrows
    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            final String endpoint = connector.getPredictEndpoint(parameters);
            final String method = connector.getPredictHttpMethod();
            final Response response = makeHttpCall(endpoint, method, payload);

            final String modelResponse = ConnectorUtils.getInputStreamContent((InputStream) response.getEntity());
            final int statusCode = response.getStatus();
            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            final ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (Exception e) {
            throw new MLException("Fail to execute predict in oci connector", e);
        }
    }

    @Override
    public InputStream invokeDownload(Map<String, String> parameters, String payload) throws IOException {
        final String endpoint = connector.getEndpoint(ConnectorAction.ActionType.DOWNLOAD, parameters);
        final String httpMethod = connector.getHttpMethod(ConnectorAction.ActionType.DOWNLOAD);
        final Response response = makeHttpCall(endpoint, httpMethod, payload);
        final int statusCode = response.getStatus();
        final InputStream responseEntity = (InputStream) response.getEntity();

        if (statusCode < 200 || statusCode >= 300) {
            final String opcRequestId = response.getHeaderString("opc-request-id");
            final String requestBodyContent = ConnectorUtils.getInputStreamContent(responseEntity);
            throw new OpenSearchStatusException(
                    REMOTE_SERVICE_ERROR + requestBodyContent + " opc request id: " + opcRequestId,
                    RestStatus.fromCode(statusCode));
        } else {
            return responseEntity;
        }
    }


     private Response makeHttpCall(String endpoint, String httpMethod, String payload) {
         final WebTarget target = getWebTarget(endpoint);
         final WrappedInvocationBuilder wrappedIb = new WrappedInvocationBuilder(target.request(), target.getUri());
         final Response response;
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
         return response;
    }

    /**
     *
     * RestClient is a general wrapper of {@link Client} to talk to OCI services however it has a poor design
     * and to support calling multiple endpoints. Basically when RestClient::setEndpoint is call it create a
     * new instance of WebTarget wit that endpoint, but instead of return that instance, customer need to make
     * another call that WebTarget instance.
     *
     * <p>For now let make it synchronize for calling setEndpoint and getBaseTarget to avoid race condition.
     * Furthermore, it is also pretty cheap compare to calling remote endpoint
     *
     * @param endpoint the web target endpoint
     * @return web target for particular endpoint
     */
    @Synchronized
    private WebTarget getWebTarget(String endpoint) {
        restClient.setEndpoint(endpoint);
        return restClient.getBaseTarget();
    }

    private static BasicAuthenticationDetailsProvider buildAuthenticationDetailsProvider(
            Connector connector) {
        final Map<String, String> parameters = connector.getParameters();
        final OciClientAuthType authType =
                OciClientAuthType.from(
                        parameters.get(
                                OciConnector.AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT));

        switch (authType) {
            case RESOURCE_PRINCIPAL:
                return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            case INSTANCE_PRINCIPAL:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case USER_PRINCIPAL:
                return SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(parameters.get(OciConnector.TENANT_ID_FIELD))
                        .userId(parameters.get(OciConnector.USER_ID_FIELD))
                        .region(Region.fromRegionCodeOrId(parameters.get(OciConnector.REGION_FIELD)))
                        .fingerprint(parameters.get(OciConnector.FINGERPRINT_FIELD))
                        .privateKeySupplier(
                                () -> {
                                    try {
                                        return new FileInputStream(parameters.get(OciConnector.PEMFILE_PATH_FIELD));
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to read private key", e);
                                    }
                                })
                        .build();
            default:
                throw new IllegalArgumentException("OCI client auth type is not supported " + authType);
        }
    }
}
