/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.script.ScriptService;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

@Log4j2
@ConnectorExecutor(AWS_SIGV4)
public class AwsConnectorExecutor implements RemoteConnectorExecutor{

    @Getter
    private AwsConnector connector;
    private final SdkHttpClient httpClient;
    @Setter @Getter
    private ScriptService scriptService;

    public AwsConnectorExecutor(Connector connector, SdkHttpClient httpClient) {
        this.connector = (AwsConnector) connector;
        this.httpClient = httpClient;
    }

    public AwsConnectorExecutor(Connector connector) {
        this(connector, new DefaultSdkHttpClientBuilder().build());
    }

    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            final String endpoint = connector.getPredictEndpoint(parameters);
            final HttpExecuteResponse response = makeHttpCall(endpoint, "POST", payload);
            final int statusCode = response.httpResponse().statusCode();

            AbortableInputStream body = null;
            if (response.responseBody().isPresent()) {
                body = response.responseBody().get();
            }

            final StringBuilder responseBuilder = new StringBuilder();
            if (body != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
            } else {
                throw new OpenSearchStatusException("No response from model", RestStatus.BAD_REQUEST);
            }

            final String modelResponse = responseBuilder.toString();

            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            final ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (IOException e) {
            throw new MLException("Fail to execute predict in aws connector", e);
        }
    }

    @Override
    public InputStream invokeDownload(Map<String, String> parameters, String payload) throws IOException {
        final String endpoint = connector.getEndpoint(ConnectorAction.ActionType.DOWNLOAD, parameters);
        final String httpMethod = connector.getHttpMethod(ConnectorAction.ActionType.DOWNLOAD);
        final HttpExecuteResponse response = makeHttpCall(endpoint, httpMethod, payload);
        final int statusCode = response.httpResponse().statusCode();
        final AbortableInputStream responseBody =
                response.responseBody().orElseThrow(
                        () -> new OpenSearchStatusException("Failed to invoke download", RestStatus.BAD_REQUEST));

        if (statusCode < 200 || statusCode >= 300) {
            throw new OpenSearchStatusException(
                    REMOTE_SERVICE_ERROR +
                            ConnectorUtils.getInputStreamContent(responseBody),
                    RestStatus.fromCode(statusCode));
        } else {
            return responseBody;
        }
    }


    private HttpExecuteResponse makeHttpCall(String endpoint, String httpMethod, String payload) {
        try {
            final SdkHttpFullRequest.Builder builder =
                    SdkHttpFullRequest.builder().uri(URI.create(endpoint));

            if (payload != null) {
                final RequestBody requestBody = RequestBody.fromString(payload);
                builder.contentStreamProvider(requestBody.contentStreamProvider());
            }

            switch (httpMethod.toUpperCase(Locale.ROOT)) {
                case "POST":
                    builder.method(POST);
                    break;
                case "GET":
                    builder.method(GET);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            final Map<String, String> headers = connector.getDecryptedHeaders();
            if (headers != null) {
                for (String key : headers.keySet()) {
                    builder.putHeader(key, headers.get(key));
                }
            }
            final SdkHttpFullRequest request = builder.build();
            final HttpExecuteRequest executeRequest = HttpExecuteRequest.builder()
                    .request(signRequest(request))
                    .contentStreamProvider(request.contentStreamProvider().orElse(null))
                    .build();

            return AccessController.doPrivileged((PrivilegedExceptionAction<HttpExecuteResponse>) () -> {
                return httpClient.prepareRequest(executeRequest).call();
            });
        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in aws connector: " + exception.getMessage(), exception);
            throw exception;
        } catch (Throwable e) {
            log.error("Failed to execute predict in aws connector", e);
            throw new MLException("Fail to execute predict in aws connector", e);
        }
    }

    private SdkHttpFullRequest signRequest(SdkHttpFullRequest request) {
        String accessKey = connector.getAccessKey();
        String secretKey = connector.getSecretKey();
        String sessionToken = connector.getSessionToken();
        String signingName = connector.getServiceName();
        String region = connector.getRegion();

        return ConnectorUtils.signRequest(request, accessKey, secretKey, sessionToken, signingName, region);
    }
}