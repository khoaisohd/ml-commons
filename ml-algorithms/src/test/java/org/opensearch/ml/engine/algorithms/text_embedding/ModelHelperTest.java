/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.connector.OciConnector.OciClientAuthType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ModelHelper modelHelper;
    private EmbeddedOciObjectStorageServer embeddedOciObjectStorageServer;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;
    private MLRegisterModelInput.MLRegisterModelInputBuilder modelInputBuilder;
    private String modelId;
    private MLEngine mlEngine;
    private String hashValue = "e13b74006290a9d0f58c1376f9629d4ebc05a0f9385f40db837452b167ae9021";

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Mock
    ActionListener<MLRegisterModelInput> registerModelListener;

    Encryptor encryptor;

    @Before
    public void setup() throws URISyntaxException, IOException {
        MockitoAnnotations.openMocks(this);
        modelFormat = MLModelFormat.TORCH_SCRIPT;
        modelConfig =
                TextEmbeddingModelConfig.builder()
                        .modelType("modelType")
                        .embeddingDimension(1)
                        .frameworkType(TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
                        .build();
        modelInputBuilder =
                MLRegisterModelInput.builder()
                        .modelName("model_name")
                        .modelFormat(modelFormat)
                        .modelConfig(modelConfig)
                        .hashValue(hashValue);

        modelId = "model_id";
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + modelId), encryptor, Settings.EMPTY);
        modelHelper = new ModelHelper(mlEngine);
        embeddedOciObjectStorageServer = new EmbeddedOciObjectStorageServer();
        embeddedOciObjectStorageServer.start();
    }

    @After
    public void tearDown() throws IOException {
        if (embeddedOciObjectStorageServer != null) {
            embeddedOciObjectStorageServer.close();
        }
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        MLRegisterModelInput modelInput = modelInputBuilder.url("http://testurl").build();
        modelHelper.downloadAndSplit(modelInput, "url_failure_model_id", "1" , FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        MLRegisterModelInput modelInput = modelInputBuilder.url(modelUrl).build();
        modelHelper.downloadAndSplit(modelInput, modelId, "1", FunctionName.TEXT_EMBEDDING, actionListener);

        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testDownloadAndSplitFromOci() throws URISyntaxException {
        final MLRegisterModelInput modelInput =
                modelInputBuilder
                        .urlConnector(
                                OciConnector
                                        .ociConnectorBuilder()
                                        .protocol(ConnectorProtocols.OCI_SIGV1)
                                        .parameters(
                                                Map.of(
                                                        OciConnector.AUTH_TYPE_FIELD, OciClientAuthType.USER_PRINCIPAL.name(),
                                                        OciConnector.REGION_FIELD, "uk-london-1",
                                                        OciConnector.TENANT_ID_FIELD, "ocid1.tenancy.oc1..aaaaaaaagkbzgg6lpzrf47xzy4rjoxg4de6ncfiq2rncmjiujvy2hjgxvziq",
                                                        OciConnector.USER_ID_FIELD, "ocid1.user.oc1..aaaaaaaajj7kdinuhkpct4rhsj7gfhyh5dja7ltcd5rrsylrozptssllagyq",
                                                        OciConnector.FINGERPRINT_FIELD, "3a:01:de:90:39:f4:b1:2f:02:75:77:c1:21:f2:20:24",
                                                        OciConnector.PEMFILE_PATH_FIELD, getClass().getClassLoader().getResource("org/opensearch/ml/engine/algorithms/oci/fakeKey.pem").toURI().getPath()))
                                        .actions(
                                                List.of(
                                                        ConnectorAction.builder()
                                                                .actionType(ConnectorAction.ActionType.DOWNLOAD)
                                                                .url(embeddedOciObjectStorageServer.getEndpoint() + "/n/idee4xpu3dvm/b/phuong-bucket/o/traced_small_model.zip")
                                                                .method("GET")
                                                                .build()))
                                        .build())
                        .build();
        modelHelper.downloadAndSplit(modelInput, modelId, "1", FunctionName.TEXT_EMBEDDING, actionListener);
        final ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testDownloadAndSplit_HashFailure() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        MLRegisterModelInput modelInput = modelInputBuilder.url(modelUrl).hashValue("wrong_hash_value").build();
        modelHelper.downloadAndSplit(modelInput, modelId, "1", FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IllegalArgumentException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit_Hash() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        MLRegisterModelInput modelInput = modelInputBuilder.url(modelUrl).build();
        modelHelper.downloadAndSplit(modelInput, modelId, "1", FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testVerifyModelZipFile() throws IOException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_ONNX() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is TORCH_SCRIPT, but find .onnx file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_TORCH_SCRIPT() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is ONNX, but find .pt file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(MLModelFormat.ONNX, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_DuplicateModelFile() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Find multiple model files, but expected only one");
        String modelUrl = getClass().getResource("traced_small_model_duplicate_pt.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_MissingTokenizer() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No tokenizer file");
        String modelUrl = getClass().getResource("traced_small_model_missing_tokenizer.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testDownloadPrebuiltModelConfig_WrongModelName() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("test_model_name")
                .version("1.0.1")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(registerModelListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadPrebuiltModelConfig() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("1.0.1")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<MLRegisterModelInput> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelInput.class);
        verify(registerModelListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        MLModelConfig modelConfig = argumentCaptor.getValue().getModelConfig();
        assertNotNull(modelConfig);
        assertEquals("mpnet", modelConfig.getModelType());
    }

    @Test
    public void testDownloadPrebuiltModelMetaList() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("1.0.1")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertEquals("huggingface/sentence-transformers/all-distilroberta-v1", ((Map<String, String>)modelMetaList.get(0)).get("name"));
    }

    @Test
    public void testIsModelAllowed_true() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("1.0.1")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertTrue(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelName() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2-wrong")
                .version("1.0.1")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelVersion() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("000")
                .modelGroupId("mockGroupId")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }
}
