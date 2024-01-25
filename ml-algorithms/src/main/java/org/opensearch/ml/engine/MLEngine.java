/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.engine.encryptor.Encryptor;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * This is the interface to all ml algorithms.
 */
@Log4j2
public class MLEngine {

    public static final Setting<String> ML_COMMON_MODEL_REPO_ENDPOINT =
            Setting.simpleString(
                    "plugins.ml_commons.model_repo_endpoint",
                    Setting.Property.NodeScope,
                    Setting.Property.Dynamic);
    public static final Setting<String> ML_COMMON_MODEL_METALIST_ENDPOINT =
            Setting.simpleString(
                    "plugins.ml_commons.model_metalist_endpoint",
                    Setting.Property.NodeScope,
                    Setting.Property.Dynamic);


    public static final String REGISTER_MODEL_FOLDER = "register";
    public static final String DEPLOY_MODEL_FOLDER = "deploy";
    private final String modelRepoEndpoint;
    private final String modelMetalistEndpoint;

    @Getter
    private final Path mlConfigPath;

    @Getter
    private final Path mlCachePath;
    private final Path mlModelsCachePath;

    private Encryptor encryptor;

    public MLEngine(Path opensearchDataFolder, Encryptor encryptor, Settings settings) {
        this.mlCachePath = opensearchDataFolder.resolve("ml_cache");
        this.mlModelsCachePath = mlCachePath.resolve("models_cache");
        this.mlConfigPath = mlCachePath.resolve("config");
        this.encryptor = encryptor;
        this.modelRepoEndpoint = ML_COMMON_MODEL_REPO_ENDPOINT.get(settings);
        this.modelMetalistEndpoint = ML_COMMON_MODEL_METALIST_ENDPOINT.get(settings);

        //TODO - This is a hack and can be fixed when djl.ai build has up to date logic
        // User might want to load their own libstdc++.so.6 instead of one provided by djl
        // Refer: https://github.com/deepjavalibrary/djl/pull/2929
        // Refer: https://github.com/deepjavalibrary/djl/issues/2919
        conditionalLoadCustomLibStdc();
    }

    private void conditionalLoadCustomLibStdc() {
        // If we want to load our own libstdc++.so.6 do it before djl does to files from cache
        final String libstd = System.getenv("LIBSTDCXX_LIBRARY_PATH");
        if (libstd != null) {
            try {
                log.info("Loading libstdc++.so.6 from: {}", libstd);
                System.load(libstd);
            } catch (UnsatisfiedLinkError e) {
                log.warn("Failed Loading libstdc++.so.6 from: {}. Falling back to default.", libstd);
            }
        }
    }

    public String getPrebuiltModelMetaListPath() {
        return this.modelMetalistEndpoint;
    }

    public String getPrebuiltModelConfigPath(String modelName, String version, MLModelFormat modelFormat) {
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        return String.format("%s/%s/%s/%s/config.json", this.modelRepoEndpoint, modelName, version, format, Locale.ROOT);
    }

    public String getPrebuiltModelPath(String modelName, String version, MLModelFormat modelFormat) {
        int index = modelName.indexOf("/") + 1;
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/sentence-transformers_msmarco-distilbert-base-tas-b-1.0.0-torch_script.zip
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/config.json
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        String modelZipFileName = modelName.substring(index).replace("/", "_") + "-" + version + "-" + format;
        return String.format("%s/%s/%s/%s/%s.zip", this.modelRepoEndpoint, modelName, version, format, modelZipFileName, Locale.ROOT);
    }

    public Path getRegisterModelPath(String modelId, String modelName, String version) {
        return getRegisterModelPath(modelId).resolve(version).resolve(modelName);
    }

    public Path getRegisterModelPath(String modelId) {
        return getRegisterModelRootPath().resolve(modelId);
    }

    public Path getRegisterModelRootPath() {
        return mlModelsCachePath.resolve(REGISTER_MODEL_FOLDER);
    }

    public Path getDeployModelPath(String modelId) {
        return getDeployModelRootPath().resolve(modelId);
    }

    public String getDeployModelZipPath(String modelId, String modelName) {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER).resolve(modelId).resolve(modelName) + ".zip";
    }

    public Path getDeployModelRootPath() {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER);
    }

    public Path getDeployModelChunkPath(String modelId, Integer chunkNumber) {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER)
                .resolve(modelId)
                .resolve("chunks")
                .resolve(chunkNumber + "");
    }

    public Path getModelCachePath(String modelId, String modelName, String version) {
        return getModelCachePath(modelId).resolve(version).resolve(modelName);
    }

    public Path getModelCachePath(String modelId) {
        return getModelCacheRootPath().resolve(modelId);
    }

    public Path getModelCacheRootPath() {
        return mlModelsCachePath.resolve("models");
    }

    public MLModel train(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Trainable trainable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainable.train(mlInput);
    }

    public Predictable deploy(MLModel mlModel, Map<String, Object> params) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModel(mlModel, params, encryptor);
        return predictable;
    }

    public MLExecutable deployExecute(MLModel mlModel, Map<String, Object> params) {
        MLExecutable executable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        executable.initModel(mlModel, params);
        return executable;
    }

    public MLOutput predict(Input input, MLModel model) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Predictable predictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (predictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return predictable.predict(mlInput, model);
    }

    public MLOutput trainAndPredict(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        TrainAndPredictable trainAndPredictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainAndPredictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainAndPredictable.trainAndPredict(mlInput);
    }

    public Output execute(Input input) throws Exception {
        validateInput(input);
        if (input.getFunctionName() == FunctionName.METRICS_CORRELATION) {
            MLExecutable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
            if (executable == null) {
                throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
            }
            return executable.execute(input);
        } else {
            Executable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
            if (executable == null) {
                throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
            }
            return executable.execute(input);
        }
    }

    private void validateMLInput(Input input) {
        validateInput(input);
        if (!(input instanceof MLInput)) {
            throw new IllegalArgumentException("Input should be MLInput");
        }
        MLInput mlInput = (MLInput) input;
        MLInputDataset inputDataset = mlInput.getInputDataset();
        if (inputDataset == null) {
            throw new IllegalArgumentException("Input data set should not be null");
        }
        if (inputDataset instanceof DataFrameInputDataset) {
            DataFrame dataFrame = ((DataFrameInputDataset)inputDataset).getDataFrame();
            if (dataFrame == null || dataFrame.size() == 0) {
                throw new IllegalArgumentException("Input data frame should not be null or empty");
            }
        }
    }

    private void validateInput(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Input should not be null");
        }
        if (input.getFunctionName() == null) {
            throw new IllegalArgumentException("Function name should not be null");
        }
    }

    public String encrypt(String credential) {
        return encryptor.encrypt(credential);
    }

}
