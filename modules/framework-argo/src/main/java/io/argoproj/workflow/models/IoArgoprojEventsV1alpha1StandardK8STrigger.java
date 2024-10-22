/*
 * Argo Workflows API
 * Argo Workflows is an open source container-native workflow engine for orchestrating parallel jobs on Kubernetes. For more information, please see https://argo-workflows.readthedocs.io/en/release-3.5/
 *
 * The version of the OpenAPI document: VERSION
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package io.argoproj.workflow.models;

import java.util.Objects;
import java.util.Arrays;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.argoproj.workflow.models.IoArgoprojEventsV1alpha1ArtifactLocation;
import io.argoproj.workflow.models.IoArgoprojEventsV1alpha1TriggerParameter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * IoArgoprojEventsV1alpha1StandardK8STrigger
 */

public class IoArgoprojEventsV1alpha1StandardK8STrigger {
  public static final String SERIALIZED_NAME_LIVE_OBJECT = "liveObject";
  @SerializedName(SERIALIZED_NAME_LIVE_OBJECT)
  private Boolean liveObject;

  public static final String SERIALIZED_NAME_OPERATION = "operation";
  @SerializedName(SERIALIZED_NAME_OPERATION)
  private String operation;

  public static final String SERIALIZED_NAME_PARAMETERS = "parameters";
  @SerializedName(SERIALIZED_NAME_PARAMETERS)
  private List<IoArgoprojEventsV1alpha1TriggerParameter> parameters = null;

  public static final String SERIALIZED_NAME_PATCH_STRATEGY = "patchStrategy";
  @SerializedName(SERIALIZED_NAME_PATCH_STRATEGY)
  private String patchStrategy;

  public static final String SERIALIZED_NAME_SOURCE = "source";
  @SerializedName(SERIALIZED_NAME_SOURCE)
  private IoArgoprojEventsV1alpha1ArtifactLocation source;


  public IoArgoprojEventsV1alpha1StandardK8STrigger liveObject(Boolean liveObject) {
    
    this.liveObject = liveObject;
    return this;
  }

   /**
   * Get liveObject
   * @return liveObject
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getLiveObject() {
    return liveObject;
  }


  public void setLiveObject(Boolean liveObject) {
    this.liveObject = liveObject;
  }


  public IoArgoprojEventsV1alpha1StandardK8STrigger operation(String operation) {
    
    this.operation = operation;
    return this;
  }

   /**
   * Get operation
   * @return operation
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getOperation() {
    return operation;
  }


  public void setOperation(String operation) {
    this.operation = operation;
  }


  public IoArgoprojEventsV1alpha1StandardK8STrigger parameters(List<IoArgoprojEventsV1alpha1TriggerParameter> parameters) {
    
    this.parameters = parameters;
    return this;
  }

  public IoArgoprojEventsV1alpha1StandardK8STrigger addParametersItem(IoArgoprojEventsV1alpha1TriggerParameter parametersItem) {
    if (this.parameters == null) {
      this.parameters = new ArrayList<IoArgoprojEventsV1alpha1TriggerParameter>();
    }
    this.parameters.add(parametersItem);
    return this;
  }

   /**
   * Parameters is the list of parameters that is applied to resolved K8s trigger object.
   * @return parameters
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "Parameters is the list of parameters that is applied to resolved K8s trigger object.")

  public List<IoArgoprojEventsV1alpha1TriggerParameter> getParameters() {
    return parameters;
  }


  public void setParameters(List<IoArgoprojEventsV1alpha1TriggerParameter> parameters) {
    this.parameters = parameters;
  }


  public IoArgoprojEventsV1alpha1StandardK8STrigger patchStrategy(String patchStrategy) {
    
    this.patchStrategy = patchStrategy;
    return this;
  }

   /**
   * Get patchStrategy
   * @return patchStrategy
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getPatchStrategy() {
    return patchStrategy;
  }


  public void setPatchStrategy(String patchStrategy) {
    this.patchStrategy = patchStrategy;
  }


  public IoArgoprojEventsV1alpha1StandardK8STrigger source(IoArgoprojEventsV1alpha1ArtifactLocation source) {
    
    this.source = source;
    return this;
  }

   /**
   * Get source
   * @return source
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public IoArgoprojEventsV1alpha1ArtifactLocation getSource() {
    return source;
  }


  public void setSource(IoArgoprojEventsV1alpha1ArtifactLocation source) {
    this.source = source;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoArgoprojEventsV1alpha1StandardK8STrigger ioArgoprojEventsV1alpha1StandardK8STrigger = (IoArgoprojEventsV1alpha1StandardK8STrigger) o;
    return Objects.equals(this.liveObject, ioArgoprojEventsV1alpha1StandardK8STrigger.liveObject) &&
        Objects.equals(this.operation, ioArgoprojEventsV1alpha1StandardK8STrigger.operation) &&
        Objects.equals(this.parameters, ioArgoprojEventsV1alpha1StandardK8STrigger.parameters) &&
        Objects.equals(this.patchStrategy, ioArgoprojEventsV1alpha1StandardK8STrigger.patchStrategy) &&
        Objects.equals(this.source, ioArgoprojEventsV1alpha1StandardK8STrigger.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(liveObject, operation, parameters, patchStrategy, source);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IoArgoprojEventsV1alpha1StandardK8STrigger {\n");
    sb.append("    liveObject: ").append(toIndentedString(liveObject)).append("\n");
    sb.append("    operation: ").append(toIndentedString(operation)).append("\n");
    sb.append("    parameters: ").append(toIndentedString(parameters)).append("\n");
    sb.append("    patchStrategy: ").append(toIndentedString(patchStrategy)).append("\n");
    sb.append("    source: ").append(toIndentedString(source)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

