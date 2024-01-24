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
import lombok.SneakyThrows;
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
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;
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

    public OciConnectorExecutor(Connector connector) {
        this.connector = (OciConnector) connector;
    }

     @Override
     @SneakyThrows
     public void invokeRemoteModel(
             MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
          final BasicAuthenticationDetailsProvider provider =
                 OciAuthProviderFactory.buildAuthenticationDetailsProvider(connector.getOciClientAuthConfig());

          final RestClientFactory restClientFactory = RestClientFactoryBuilder.builder().build();
          final RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);

          // Currently let use restClient from OCI Java SDK 2 since the OCI Java SDK 3 won't work for
          // the multi-class-loader plugin architecture of OpenSearch
          // @TODO We need to reuse restClient instead of keep creating new client for every request
          // https://jira.oci.oraclecorp.com/browse/ES-5193
          try (final RestClient restClient = restClientFactory.create(requestSigner, Collections.emptyMap())) {
              final String endpoint = connector.getPredictEndpoint(parameters);
              restClient.setEndpoint(endpoint);
              final WebTarget target = restClient.getBaseTarget();
              final WrappedInvocationBuilder wrappedIb = new WrappedInvocationBuilder(target.request(), target.getUri());
              final Response response = restClient.post(wrappedIb, payload, new BmcRequest<>());
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
              log.error("Failed to execute predict in oci connector: {}", exception.getMessage(), exception);
              throw exception;
          }
    }
}
