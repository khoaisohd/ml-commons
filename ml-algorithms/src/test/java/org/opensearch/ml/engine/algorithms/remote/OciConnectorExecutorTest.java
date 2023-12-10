package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.oci.OciClientAuthType;
import org.opensearch.ml.common.oci.OciClientUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OciConnectorExecutorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private EmbeddedOciGenaiServer embeddedOciGenaiServer;

    @Before
    @SneakyThrows
    public void setUp() {
        embeddedOciGenaiServer = new EmbeddedOciGenaiServer();
        embeddedOciGenaiServer.start();
    }

    @After
    @SneakyThrows
    public void tearDown() {
        if (embeddedOciGenaiServer != null) {
            embeddedOciGenaiServer.close();
        }
    }

    @Test
    @SneakyThrows
    public void executePredict_RemoteInferenceInput() {
        final ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url(EmbeddedOciGenaiServer.BASE_URI + "/20231130/actions/generateText")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();

        final Map<String, String> parameters =
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, OciClientAuthType.USER_PRINCIPAL.name(),
                        OciClientUtils.REGION_FIELD, "uk-london-1",
                        OciClientUtils.TENANT_ID_FIELD, "ocid1.tenancy.oc1..aaaaaaaagkbzgg6lpzrf47xzy4rjoxg4de6ncfiq2rncmjiujvy2hjgxvziq",
                        OciClientUtils.USER_ID_FIELD, "ocid1.user.oc1..aaaaaaaajj7kdinuhkpct4rhsj7gfhyh5dja7ltcd5rrsylrozptssllagyq",
                        OciClientUtils.FINGERPRINT_FIELD, "3a:01:de:90:39:f4:b1:2f:02:75:77:c1:21:f2:20:24",
                        OciClientUtils.PEMFILE_PATH_FIELD, getClass().getClassLoader().getResource("org/opensearch/ml/engine/algorithms/text_embedding/fakeKey.pem").toURI().getPath());

        final Connector connector =
                OciConnector
                        .ociConnectorBuilder()
                        .name("test connector")
                        .version("1")
                        .protocol("http")
                        .parameters(parameters)
                        .credential(Map.of())
                        .actions(Collections.singletonList(predictAction)).build();

        final OciConnectorExecutor executor = new OciConnectorExecutor(connector);

        final MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        final ModelTensorOutput modelTensorOutput = executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Map<String, Object> expectedResponse =
                Map.of(
                        "generatedTexts",
                        List.of(List.of(Map.of("text", "answer"))));

        Assert.assertEquals(
                expectedResponse,
                modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap());
    }

    @Test
    @SneakyThrows
    public void executePredict_RemoteInferenceInput_WrongEndpoint() {
        exceptionRule.expect(OpenSearchStatusException.class);

        final ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url(EmbeddedOciGenaiServer.BASE_URI + "/20231130/actions/wrongEndpoint")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();

        final Map<String, String> credential =
                Map.of(
                        OciClientUtils.AUTH_TYPE_FIELD, OciClientAuthType.USER_PRINCIPAL.name(),
                        OciClientUtils.REGION_FIELD, "uk-london-1",
                        OciClientUtils.TENANT_ID_FIELD, "ocid1.tenancy.oc1..aaaaaaaagkbzgg6lpzrf47xzy4rjoxg4de6ncfiq2rncmjiujvy2hjgxvziq",
                        OciClientUtils.USER_ID_FIELD, "ocid1.user.oc1..aaaaaaaajj7kdinuhkpct4rhsj7gfhyh5dja7ltcd5rrsylrozptssllagyq",
                        OciClientUtils.FINGERPRINT_FIELD, "3a:01:de:90:39:f4:b1:2f:02:75:77:c1:21:f2:20:24",
                        OciClientUtils.PEMFILE_PATH_FIELD, getClass().getClassLoader().getResource("org/opensearch/ml/engine/algorithms/text_embedding/fakeKey.pem").toURI().getPath());

        final Map<String, String> parameters = Map.of();
        final Connector connector =
                OciConnector
                        .ociConnectorBuilder()
                        .name("test connector")
                        .version("1")
                        .protocol("http")
                        .parameters(parameters)
                        .credential(credential)
                        .actions(Collections.singletonList(predictAction)).build();

        final OciConnectorExecutor executor = new OciConnectorExecutor(connector);

        final MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
    }
}
