/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.engine.utils.FileUtils.splitFileIntoChunks;

@Log4j2
public class ModelHelper {
    public static final String CHUNK_FILES = "chunk_files";
    public static final String MODEL_SIZE_IN_BYTES = "model_size_in_bytes";
    public static final String MODEL_FILE_HASH = "model_file_hash";
    public static final int CHUNK_SIZE = 10_000_000; // 10MB
    public static final String PYTORCH_FILE_EXTENSION = ".pt";
    public static final String ONNX_FILE_EXTENSION = ".onnx";
    public static final String TOKENIZER_FILE_NAME = "tokenizer.json";
    public static final String PYTORCH_ENGINE = "PyTorch";
    public static final String ONNX_ENGINE = "OnnxRuntime";
    private static final String OCI_OS_SCHEME = "oci-os";
    private final MLEngine mlEngine;

    public ModelHelper(MLEngine mlEngine) {
        this.mlEngine = mlEngine;
    }

    public void downloadPrebuiltModelConfig(String taskId, MLRegisterModelInput registerModelInput, ActionListener<MLRegisterModelInput> listener) {
        log.info("DebugDebug downloadPrebuiltModelConfig {}", taskId);
        String modelName = registerModelInput.getModelName();
        String version = registerModelInput.getVersion();
        MLModelFormat modelFormat = registerModelInput.getModelFormat();
        boolean deployModel = registerModelInput.isDeployModel();
        String[] modelNodeIds = registerModelInput.getModelNodeIds();
        String modelGroupId = registerModelInput.getModelGroupId();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {

                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                log.info("DebugDebug 2 {}", registerModelPath);
                String configCacheFilePath = registerModelPath.resolve("config.json").toString();

                String configFileUrl = mlEngine.getPrebuiltModelConfigPath(modelName, version, modelFormat);
                String modelZipFileUrl = mlEngine.getPrebuiltModelPath(modelName, version, modelFormat);
                log.info("DebugDebug 3 {} {}", configFileUrl, modelZipFileUrl);
                DownloadUtils.download(configFileUrl, configCacheFilePath, new ProgressBar());

                Map<?, ?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(configCacheFilePath))) {
                    config = gson.fromJson(reader, Map.class);
                }

                if (config == null) {
                    listener.onFailure(new IllegalArgumentException("model config not found"));
                    return null;
                }

                MLRegisterModelInput.MLRegisterModelInputBuilder builder = MLRegisterModelInput.builder();

                builder.modelName(modelName)
                        .version(version)
                        .url(modelZipFileUrl)
                        .deployModel(deployModel)
                        .modelNodeIds(modelNodeIds)
                        .modelGroupId(modelGroupId);
                config.entrySet().forEach(entry -> {
                    switch (entry.getKey().toString()) {
                        case MLRegisterModelInput.MODEL_FORMAT_FIELD:
                            builder.modelFormat(MLModelFormat.from(entry.getValue().toString()));
                            break;
                        case MLRegisterModelInput.MODEL_CONFIG_FIELD:
                            TextEmbeddingModelConfig.TextEmbeddingModelConfigBuilder configBuilder = TextEmbeddingModelConfig.builder();
                            Map<?, ?> configMap = (Map<?, ?>) entry.getValue();
                            for (Map.Entry<?, ?> configEntry : configMap.entrySet()) {
                                switch (configEntry.getKey().toString()) {
                                    case MLModelConfig.MODEL_TYPE_FIELD:
                                        configBuilder.modelType(configEntry.getValue().toString());
                                        break;
                                    case MLModelConfig.ALL_CONFIG_FIELD:
                                        configBuilder.allConfig(configEntry.getValue().toString());
                                        break;
                                    case TextEmbeddingModelConfig.EMBEDDING_DIMENSION_FIELD:
                                        configBuilder.embeddingDimension(((Double)configEntry.getValue()).intValue());
                                        break;
                                    case TextEmbeddingModelConfig.FRAMEWORK_TYPE_FIELD:
                                        configBuilder.frameworkType(TextEmbeddingModelConfig.FrameworkType.from(configEntry.getValue().toString()));
                                        break;
                                    case TextEmbeddingModelConfig.POOLING_MODE_FIELD:
                                        configBuilder.poolingMode(TextEmbeddingModelConfig.PoolingMode.from(configEntry.getValue().toString().toUpperCase(Locale.ROOT)));
                                        break;
                                    case TextEmbeddingModelConfig.NORMALIZE_RESULT_FIELD:
                                        configBuilder.normalizeResult(Boolean.parseBoolean(configEntry.getValue().toString()));
                                        break;
                                    case TextEmbeddingModelConfig.MODEL_MAX_LENGTH_FIELD:
                                        configBuilder.modelMaxLength(((Double)configEntry.getValue()).intValue());
                                        break;
                                    default:
                                        break;
                                }
                            }
                            builder.modelConfig(configBuilder.build());
                            break;
                        case MLRegisterModelInput.HASH_VALUE_FIELD:
                            builder.hashValue(entry.getValue().toString());
                            break;
                        default:
                            break;
                    }
                });
                listener.onResponse(builder.build());
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        } finally {
            deleteFileQuietly(mlEngine.getRegisterModelPath(taskId));
        }
    }

    public boolean isModelAllowed(MLRegisterModelInput registerModelInput, List modelMetaList) {
        String modelName = registerModelInput.getModelName();
        String version = registerModelInput.getVersion();
        MLModelFormat modelFormat = registerModelInput.getModelFormat();
        for (Object meta: modelMetaList) {
            String name = (String) ((Map<String, Object>)meta).get("name");
            List<String> versions = (List) ((Map<String, Object>)meta).get("version");
            List<String> formats = (List) ((Map<String, Object>)meta).get("format");
            if (name.equals(modelName) && versions.contains(version.toLowerCase(Locale.ROOT)) && formats.contains(modelFormat.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public List downloadPrebuiltModelMetaList(String taskId, MLRegisterModelInput registerModelInput)  {
        String modelName = registerModelInput.getModelName();
        String version = registerModelInput.getVersion();
        log.info("DebugDebug !! I am here ! {} {}", modelName, version);
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<List>) () -> {

                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                String cacheFilePath = registerModelPath.resolve("model_meta_list.json").toString();
                String modelMetaListUrl = mlEngine.getPrebuiltModelMetaListPath();
                log.info("DebugDebug !! I am here - 2 ! {} ", modelMetaListUrl);

                DownloadUtils.download(modelMetaListUrl, cacheFilePath, new ProgressBar());

                List<?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(cacheFilePath))) {
                    config = gson.fromJson(reader, List.class);
                }

                return config;
            });
        } catch (Exception e){
            log.info("DebugDebug !! I am here - 3 ! {} ", e.getMessage());
        }
        finally {
            deleteFileQuietly(mlEngine.getRegisterModelPath(taskId));
        }
        return null;

    }

    /**
     * Download model from URL and split it into smaller chunks.
     * @param registerModelInput register model input
     * @param taskId task id
     * @param version model version
     * @param listener action listener
     */
    public void downloadAndSplit(MLRegisterModelInput registerModelInput, String taskId, String version, FunctionName functionName, ActionListener<Map<String, Object>> listener) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                final String modelName = registerModelInput.getModelName();
                final MLModelFormat modelFormat = registerModelInput.getModelFormat();
                final String modelContentHash = registerModelInput.getHashValue();
                final String url = registerModelInput.getUrl();
                final URI uri = new URI(url);
                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                String modelPath = registerModelPath +".zip";
                Path modelPartsPath = registerModelPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                log.debug("download model to file {}", modelZipFile.getAbsolutePath());
                if (OCI_OS_SCHEME.equals(uri.getScheme())) {
                    downloadFromOciObjectStorage(registerModelInput, uri, modelPath);
                } else {
                    DownloadUtils.download(url, modelPath, new ProgressBar());
                }
                verifyModelZipFile(modelFormat, modelPath, modelName, functionName);
                String hash = calculateFileHash(modelZipFile);
                if (hash.equals(modelContentHash)) {
                    List<String> chunkFiles = splitFileIntoChunks(modelZipFile, modelPartsPath, CHUNK_SIZE);
                    Map<String, Object> result = new HashMap<>();
                    result.put(CHUNK_FILES, chunkFiles);
                    result.put(MODEL_SIZE_IN_BYTES, modelZipFile.length());

                    result.put(MODEL_FILE_HASH, calculateFileHash(modelZipFile));
                    deleteFileQuietly(modelZipFile);
                    listener.onResponse(result);
                    return null;
                } else {
                    log.error("Model content hash can't match original hash value when registering");
                    throw (new IllegalArgumentException("model content changed"));
                }
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Download model from object storage
     * uri format: oci-os://{namespace}/{bucketName}/{objectName}
     */
    private void downloadFromOciObjectStorage(
            final MLRegisterModelInput registerModelInput,
            final URI uri,
            final String targetFilePath) {
        final String namespace = uri.getHost();
        // url path is expected to have format /{bucketName}/{objectName}
        final String[] parts = uri.getPath().split("/");
        Preconditions.checkArgument(
                parts.length == 3, "Invalid OCI object storage URI %s", registerModelInput.getUrl());
        final String bucket = parts[1];
        final String object = parts[2];

        log.debug(
                "Downloading model, endpoint: {}, namespace: {}, bucket: {}, object: {}",
                registerModelInput.getOciOsEndpoint(),
                namespace,
                bucket,
                object);

        final BasicAuthenticationDetailsProvider authenticationDetails =
                getAuthenticationDetailsProvider(registerModelInput);

        try (final ObjectStorage objectStorage =
                     ObjectStorageClient
                             .builder()
                             .endpoint(registerModelInput.getOciOsEndpoint())
                             .build(authenticationDetails);
             final InputStream inStream = objectStorage.getObject(
                     GetObjectRequest.builder()
                             .namespaceName(namespace)
                             .bucketName(bucket)
                             .objectName(object)
                             .build()).getInputStream()) {
            final File destinationFile = new File(targetFilePath);
            FileUtils.forceMkdir(destinationFile.getParentFile());
            Files.copy(inStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to download file from object storage " + registerModelInput, ex);
        }
    }

    private static BasicAuthenticationDetailsProvider getAuthenticationDetailsProvider(
            final MLRegisterModelInput registerModelInput) {
        final MLRegisterModelInput.OciClientAuthType ociClientAuthType = registerModelInput.getOciClientAuthType();
        log.debug("Get auth details for OCI client auth type: {}", ociClientAuthType);

        switch (ociClientAuthType) {
            case RESOURCE_PRINCIPAL:
                return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            case INSTANCE_PRINCIPAL:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case USER_PRINCIPAL:
                return SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(registerModelInput.getOciClientAuthTenantId())
                        .userId(registerModelInput.getOciClientAuthUserId())
                        .region(Region.fromRegionCodeOrId(registerModelInput.getOciClientAuthRegion()))
                        .fingerprint(registerModelInput.getOciClientAuthFingerprint())
                        .privateKeySupplier(
                                () -> {
                                    try {
                                        return new FileInputStream(registerModelInput.getOciClientAuthPemfilepath());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .build();
            default:
                throw new IllegalArgumentException("OCI client auth type is not supported " + ociClientAuthType);
        }
    }

    public void verifyModelZipFile(MLModelFormat modelFormat, String modelZipFilePath, String modelName, FunctionName functionName) throws IOException {
        boolean hasPtFile = false;
        boolean hasOnnxFile = false;
        boolean hasTokenizerFile = false;
        try (ZipFile zipFile = new ZipFile(modelZipFilePath)) {
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = ((ZipEntry) zipEntries.nextElement()).getName();
                hasPtFile = hasModelFile(modelFormat, MLModelFormat.TORCH_SCRIPT, PYTORCH_FILE_EXTENSION, hasPtFile, fileName);
                hasOnnxFile = hasModelFile(modelFormat, MLModelFormat.ONNX, ONNX_FILE_EXTENSION, hasOnnxFile, fileName);
                if (fileName.equals(TOKENIZER_FILE_NAME)) {
                    hasTokenizerFile = true;
                }
            }
        }
        if (!hasPtFile && !hasOnnxFile && functionName != FunctionName.SPARSE_TOKENIZE) { // sparse tokenizer model doesn't need model file.
            throw new IllegalArgumentException("Can't find model file");
        }
        if (!hasTokenizerFile) {
            if (modelName != FunctionName.METRICS_CORRELATION.toString()) {
                throw new IllegalArgumentException("No tokenizer file");
            }
        }
    }

    private static boolean hasModelFile(MLModelFormat modelFormat, MLModelFormat targetModelFormat, String fileExtension, boolean hasModelFile, String fileName) {
        if (fileName.endsWith(fileExtension)) {
            if (modelFormat != targetModelFormat) {
                throw new IllegalArgumentException("Model format is " + modelFormat + ", but find " + fileExtension + " file");
            }
            if (hasModelFile) {
                throw new IllegalArgumentException("Find multiple model files, but expected only one");
            }
            return true;
        }
        return hasModelFile;
    }

    public void deleteFileCache(String modelId) {
        deleteFileQuietly(mlEngine.getModelCachePath(modelId));
        deleteFileQuietly(mlEngine.getDeployModelPath(modelId));
        deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
    }

}
