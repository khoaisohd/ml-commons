/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Data;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processInput;

public interface RemoteConnectorExecutor {

    default ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();

        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            int processedDocs = 0;
            while(processedDocs < textDocsInputDataSet.getDocs().size()) {
                List<String> textDocs = textDocsInputDataSet.getDocs().subList(processedDocs, textDocsInputDataSet.getDocs().size());
                List<ModelTensors> tempTensorOutputs = new ArrayList<>();
                preparePayloadAndInvokeRemoteModel(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(TextDocsInputDataSet.builder().docs(textDocs).build()).build(), tempTensorOutputs);
                processedDocs += Math.max(tempTensorOutputs.size(), 1);
                tensorOutputs.addAll(tempTensorOutputs);
            }

        } else {
            preparePayloadAndInvokeRemoteModel(mlInput, tensorOutputs);
        }
        return new ModelTensorOutput(tensorOutputs);
    }
    default void setScriptService(ScriptService scriptService){}
    ScriptService getScriptService();
    Connector getConnector();
    default void setClient(Client client){}
    default void setXContentRegistry(NamedXContentRegistry xContentRegistry){}
    default void setClusterService(ClusterService clusterService){}

    default void preparePayloadAndInvokeRemoteModel(MLInput mlInput, List<ModelTensors> tensorOutputs) {
        Connector connector = getConnector();

        Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        MLInputDataset inputDataset = mlInput.getInputDataset();
        if (inputDataset instanceof RemoteInferenceInputDataSet && ((RemoteInferenceInputDataSet) inputDataset).getParameters() != null) {
            parameters.putAll(((RemoteInferenceInputDataSet) inputDataset).getParameters());
        }

        RemoteInferenceInputDataSet inputData = processInput(mlInput, connector, parameters, getScriptService());
        if (inputData.getParameters() != null) {
            parameters.putAll(inputData.getParameters());
        }
        String payload = connector.createPredictPayload(parameters);
        connector.validatePayload(payload);
        invokeRemoteModel(mlInput, parameters, payload, tensorOutputs);
    }

    default InputStream executeDownload(Map<String, String> downloadParameters) throws IOException {
        final Connector connector = getConnector();

        final Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        
        if (downloadParameters != null) {
            parameters.putAll(downloadParameters);
        }

        final String payload = connector.createPayload(ConnectorAction.ActionType.DOWNLOAD, parameters);
        connector.validatePayload(payload);
        return invokeDownload(parameters, payload);
    }

    void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs);

    /**
     * Execute http call on a remote service via http protocol
     * @param parameters the action parameters
     * @param payload the request payload
     * @return the {@link HttpResponse}
     */
    InputStream invokeDownload(Map<String, String> parameters, String payload) throws IOException;

    @Data
    class HttpResponse {
        /**
         * The response body
         */
        private final InputStream body;

        /**
         * The response status code
         */
        private final int statusCode;
    }
}
