package org.opensearch.ml.engine.algorithms.text_embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.io.ByteStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.common.regex.Regex;
import org.opensearch.core.rest.RestStatus;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * EmbeddedOciObjectStorageServer is in-memory mock server for unit test that needs
 * to call OCI object storage
 */
@Slf4j
public class EmbeddedOciObjectStorageServer implements Closeable {
    public static final String BASE_URI = "http://localhost:8080/";
    /**
     * Hardcode PORT for now to make it simple. We should find the available port
     * programmatically instead
     */
    private static final int PORT = 8080;
    private static final String URL = "localhost";
    private final HttpServer server;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public EmbeddedOciObjectStorageServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(URL), PORT), 0);
        this.server.createContext("/", new OciHttpHandler());
    }

    public void start() {
        executorService.submit(
                () -> {
                    try {
                        server.start();
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        executorService.shutdownNow();
    }

    public static class OciHttpHandler implements HttpHandler {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        static {
            // Prevent exceptions from being thrown for unknown properties
            MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            MAPPER.configure(FAIL_ON_IGNORED_PROPERTIES, false);
            MAPPER.setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false));
        }

        public OciHttpHandler() {
            log.info("Initializing OciHttpHandler");
        }

        @Override
        public void handle(HttpExchange exchange) {
            final String path = exchange.getRequestURI().getPath();
            final String[] pathParams = path.split("/");
            try (exchange) {
                if (Regex.simpleMatch("/n/*/b/*/o/*", path)
                        && exchange.getRequestMethod().equals("GET")) {
                    // GET object
                    final String namespace = pathParams[2];
                    final String bucket = pathParams[4];
                    final String objectName =
                            String.join("/", Arrays.copyOfRange(pathParams, 6, pathParams.length));
                    getObject(namespace, bucket, objectName, exchange);
                } else {
                    throw new RuntimeException("Only getObject is supported");
                }
            }
        }

        @SneakyThrows
        private void getObject(
                String namespaceName, String bucketName, String objectName, HttpExchange exchange) {
            log.info(
                    "Get object with namespaceName:{}, bucketName: {}, objectName: {}",
                    namespaceName,
                    bucketName,
                    objectName);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(RestStatus.OK.getStatus(), 0);

            try(InputStream inputStream = getClass().getResourceAsStream(objectName)) {
                ByteStreams.copy(inputStream, exchange.getResponseBody());
            }
        }
    }
}
