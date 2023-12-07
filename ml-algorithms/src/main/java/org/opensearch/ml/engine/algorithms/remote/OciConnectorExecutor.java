/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.Priorities;
import com.oracle.bmc.http.client.HttpClient;
import com.oracle.bmc.http.client.HttpRequest;
import com.oracle.bmc.http.client.HttpResponse;
import com.oracle.bmc.http.client.Method;
import com.oracle.bmc.http.client.jersey.JerseyHttpProvider;
import com.oracle.bmc.http.signing.RequestSigningFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.oci.OciClientUtils;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.oci.OciAuthProviderFactory;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;

import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_GENAI;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

/**
 * OciConnectorExecutor is responsible to call remote model from OCI services
 */
@Log4j2
@ConnectorExecutor(OCI_GENAI)
public class OciConnectorExecutor implements RemoteConnectorExecutor{

    @Getter
    private final OciConnector connector;
    @Setter @Getter
    private ScriptService scriptService;

    public OciConnectorExecutor(Connector connector) {
        this.connector = (OciConnector) connector;
    }

     @Override
     public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
         OciClientUtils.validateCredential(connector.getCredential());
         final String endpoint = connector.getPredictEndpoint(parameters);
         final Method httpMethod = Method.valueOf(connector.getPredictHttpMethod().toUpperCase(Locale.ROOT));
         final BasicAuthenticationDetailsProvider provider =
                 OciAuthProviderFactory.buildAuthenticationDetailsProvider(connector.getCredential());

         final RequestSigningFilter requestSigningFilter = RequestSigningFilter.fromAuthProvider(provider);

         try (final HttpClient client =
                     JerseyHttpProvider.getInstance()
                             .newBuilder()
                             .registerRequestInterceptor(Priorities.AUTHENTICATION, requestSigningFilter)
                             .baseUri(URI.create(endpoint))
                             .build()) {

             final HttpRequest request =
                    client
                            .createRequest(httpMethod).header("accepts", MediaType.APPLICATION_JSON)
                            .body(payload);
             final HttpResponse response = request.execute().toCompletableFuture().get();
             final InputStream responseBody = response.streamBody().toCompletableFuture().get();
             final int statusCode = response.status();

             if (statusCode < 200 || statusCode >= 300) {
                 throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + responseBody, RestStatus.fromCode(statusCode));
             }

            try (final BufferedReader reader =
                         new BufferedReader(new InputStreamReader(responseBody))) {
                final StringBuilder jsonBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
                final ModelTensors tensors = processOutput(jsonBody.toString(), connector, scriptService, parameters);
                tensors.setStatusCode(statusCode);
                tensorOutputs.add(tensors);
            }
        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in aws connector: " + exception.getMessage(), exception);
            throw exception;
        } catch (Throwable e) {
            log.error("Failed to execute predict in aws connector", e);
            throw new MLException("Fail to execute predict in aws connector", e);
        }
    }
}
