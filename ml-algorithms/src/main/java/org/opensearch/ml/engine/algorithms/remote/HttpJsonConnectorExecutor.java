/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private HttpConnector connector;
    @Setter @Getter
    private ScriptService scriptService;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (HttpConnector)connector;
    }

    @Override
    public Response executeRemoteCall(String endpoint, String httpMethod, String payload) {
        try {
            AtomicReference<Response> responseRef = new AtomicReference<>();

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
                        request = new HttpGet(endpoint);
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
                try (CloseableHttpClient httpClient = getHttpClient();
                     CloseableHttpResponse response = httpClient.execute(request)) {
                    responseRef.set(
                            new Response(
                                    response.getEntity().getContent(),
                                    response.getStatusLine().getStatusCode()));
                }
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

    public CloseableHttpClient getHttpClient() {
        return MLHttpClientFactory.getCloseableHttpClient();
    }
}
