/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
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
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.oci.OciAuthProviderFactory;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;

import javax.net.ssl.SSLSession;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
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
     public void invokeRemoteModel(
             MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
          final BasicAuthenticationDetailsProvider provider =
                 OciAuthProviderFactory.buildAuthenticationDetailsProvider(connector.getParameters());
          final RequestSigningFilter requestSigningFilter = RequestSigningFilter.fromAuthProvider(provider);
          final Client client = ClientBuilder
                  .newBuilder()
                  .hostnameVerifier((String hostname, SSLSession session) -> true)
                  .build()
                  .register(requestSigningFilter);

         try (Response response = client.target(connector.getPredictEndpoint(parameters))
                 .request(MediaType.APPLICATION_JSON)
                 .post(Entity.json(payload))) {
             final InputStream responseBody = (InputStream) response.getEntity();
             final int statusCode = response.getStatus();
             final StringBuilder jsonBody = new StringBuilder();

             try (final BufferedReader reader =
                          new BufferedReader(new InputStreamReader(responseBody))) {
                 String line;
                 while ((line = reader.readLine()) != null) {
                     jsonBody.append(line);
                 }
             }

             if (statusCode < 200 || statusCode >= 300) {
                 throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + jsonBody, RestStatus.fromCode(statusCode));
             } else {
                 final ModelTensors tensors = processOutput(jsonBody.toString(), connector, scriptService, parameters);
                 tensors.setStatusCode(statusCode);
                 tensorOutputs.add(tensors);
             }

        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in oci connector: " + exception.getMessage(), exception);
            throw exception;
        } catch (Throwable e) {
            log.error("Failed to execute predict in oci connector", e);
            throw new MLException("Failed to execute predict in oci connector", e);
        } finally {
             client.close();
         }
    }
}
