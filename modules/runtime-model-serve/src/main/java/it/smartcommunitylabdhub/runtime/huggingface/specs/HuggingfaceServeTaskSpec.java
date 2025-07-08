/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.runtime.huggingface.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.huggingface.HuggingfaceServeRuntime;
import it.smartcommunitylabdhub.runtime.huggingface.models.HuggingfaceBackend;
import it.smartcommunitylabdhub.runtime.huggingface.models.HuggingfaceDType;
import it.smartcommunitylabdhub.runtime.huggingface.models.HuggingfaceTask;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HuggingfaceServeRuntime.RUNTIME, kind = HuggingfaceServeTaskSpec.KIND, entity = EntityName.TASK)
public class HuggingfaceServeTaskSpec extends ModelServeServeTaskSpec {

    public static final String KIND = HuggingfaceServeRuntime.RUNTIME + "+serve";

    // The ML task name
    @JsonProperty("huggingface_task")
    @Schema(title = "fields.huggingface.task.title", description = "fields.huggingface.task.description")
    private HuggingfaceTask huggingfaceTask;

    // the backend to use to load the model.
    @JsonProperty("backend")
    @Schema(title = "fields.huggingface.backend.title", description = "fields.huggingface.backend.description")
    private HuggingfaceBackend backend;

    // Huggingface tokenizer revision
    @JsonProperty("tokenizer_revision")
    @Schema(
        title = "fields.huggingface.tokenizerrevision.title",
        description = "fields.huggingface.tokenizerrevision.description"
    )
    private String tokenizerRevision;

    // Huggingface max sequence length for the tokenizer
    @JsonProperty("max_length")
    @Schema(title = "fields.huggingface.maxlength.title", description = "fields.huggingface.maxlength.description")
    private Integer maxLength;

    // do not use lower case for the tokenizer
    @JsonProperty("disable_lower_case")
    @Schema(
        title = "fields.huggingface.disablelowercase.title",
        description = "fields.huggingface.disablelowercase.description"
    )
    private Boolean disableLowerCase;

    // the sequences will not be encoded with the special tokens relative to their model
    @JsonProperty("disable_special_tokens")
    @Schema(
        title = "fields.huggingface.disablespecialtokens.title",
        description = "fields.huggingface.disablespecialtokens.description"
    )
    private Boolean disableSpecialTokens;

    // data type to load the weights in.
    @JsonProperty("dtype")
    @Schema(title = "fields.huggingface.dtype.title", description = "fields.huggingface.dtype.description")
    private HuggingfaceDType dtype;

    // allow loading of models and tokenizers with custom code
    @JsonProperty("trust_remote_code")
    @Schema(
        title = "fields.huggingface.trustremotecode.title",
        description = "fields.huggingface.trustremotecode.description"
    )
    private Boolean trustRemoteCode;

    // the tensor input names passed to the model
    @JsonProperty("tensor_input_names")
    @Schema(
        title = "fields.huggingface.tensorinputnames.title",
        description = "fields.huggingface.tensorinputnames.description"
    )
    private List<String> tensorInputNames;

    // Return token type ids
    @JsonProperty("return_token_type_ids")
    @Schema(
        title = "fields.huggingface.returntokentypeids.title",
        description = "fields.huggingface.returntokentypeids.description"
    )
    private Boolean returnTokenTypeIds;

    // Return all probabilities
    @JsonProperty("return_probabilities")
    @Schema(
        title = "fields.huggingface.returnprobabilities.title",
        description = "fields.huggingface.returnprobabilities.description"
    )
    private Boolean returnProbabilities;

    // Disable logging requests
    @JsonProperty("disable_log_requests")
    @Schema(
        title = "fields.huggingface.disablelogrequests.title",
        description = "fields.huggingface.disablelogrequests.description"
    )
    private Boolean disableLogRequests;

    // Max number of prompt characters or prompt
    @JsonProperty("max_log_len")
    @Schema(title = "fields.huggingface.maxloglen.title", description = "fields.huggingface.maxloglen.description")
    private Integer maxLogLen;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        HuggingfaceServeTaskSpec spec = mapper.convertValue(data, HuggingfaceServeTaskSpec.class);
        this.huggingfaceTask = spec.getHuggingfaceTask();

        this.backend = spec.getBackend();
        this.tokenizerRevision = spec.getTokenizerRevision();
        this.maxLength = spec.getMaxLength();
        this.disableLowerCase = spec.getDisableLowerCase();
        this.disableSpecialTokens = spec.getDisableSpecialTokens();
        this.dtype = spec.getDtype();
        this.trustRemoteCode = spec.getTrustRemoteCode();
        this.tensorInputNames = spec.getTensorInputNames();
        this.returnTokenTypeIds = spec.getReturnTokenTypeIds();
        this.returnProbabilities = spec.getReturnProbabilities();
        this.disableLogRequests = spec.getDisableLogRequests();
        this.maxLogLen = spec.getMaxLogLen();
    }

    public static HuggingfaceServeTaskSpec with(Map<String, Serializable> data) {
        HuggingfaceServeTaskSpec spec = new HuggingfaceServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
