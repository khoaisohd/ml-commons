/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private HttpConnector connector;
    @Setter @Getter
    private ScriptService scriptService;

    private final CloseableHttpClient httpClient;

    public HttpJsonConnectorExecutor(Connector connector, CloseableHttpClient httpClient) {
        this.connector = (HttpConnector)connector;
        this.httpClient = httpClient;
    }

    public HttpJsonConnectorExecutor(Connector connector) {
        this(connector, MLHttpClientFactory.getCloseableHttpClient());
    }

    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            final String endpoint = connector.getPredictEndpoint(parameters);
            final String method = connector.getPredictHttpMethod();
            final HttpResponse httpResponse = makeHttpCall(endpoint, method, parameters, payload);
            final int statusCode = httpResponse.getStatusLine().getStatusCode();
            final InputStream responseBody = httpResponse.getEntity().getContent();
            final String modelResponse = ConnectorUtils.getInputStreamContent(responseBody);

            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            final ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (Exception e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        }
    }

    public InputStream invokeDownload(Map<String, String> parameters, String payload) throws IOException {
        final String endpoint = connector.getEndpoint(ConnectorAction.ActionType.DOWNLOAD, parameters);
        final String httpMethod = connector.getHttpMethod(ConnectorAction.ActionType.DOWNLOAD);
        final HttpResponse httpResponse = makeHttpCall(endpoint, httpMethod, parameters, payload);
        final int statusCode = httpResponse.getStatusLine().getStatusCode();
        final InputStream responseBody = httpResponse.getEntity().getContent();

        if (statusCode < 200 || statusCode >= 300) {
            throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR +
                    ConnectorUtils.getInputStreamContent(responseBody), RestStatus.fromCode(statusCode));
        } else {
            return responseBody;
        }
    }

    private HttpResponse makeHttpCall(String endpoint, String httpMethod, Map<String, String> parameters, String payload) {
        try {
            AtomicReference<HttpResponse> responseRef = new AtomicReference<>();

            HttpUriRequest request;
            switch (httpMethod.toUpperCase(Locale.ROOT)) {
                case "POST":
                    try {
                        request = new HttpPost(endpoint);
                        HttpEntity entity = new StringEntity(payload);
                        ((HttpPost)request).setEntity(entity);
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                case "GET":
                    try {
                        request = new HttpGet(connector.getPredictEndpoint(parameters));
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            Map<String, ?> headers = connector.getDecryptedHeaders();
            boolean hasContentTypeHeader = false;
            if (headers != null) {
                for (String key : headers.keySet()) {
                    request.addHeader(key, (String)headers.get(key));
                    if (key.toLowerCase().equals("Content-Type")) {
                        hasContentTypeHeader = true;
                    }
                }
            }
            if (!hasContentTypeHeader) {
                request.addHeader("Content-Type", "application/json");
            }

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                final HttpResponse response = httpClient.execute(request);
                responseRef.set(response);
                return null;
            });
            return responseRef.get();
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            throw e;
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        }
    }

}