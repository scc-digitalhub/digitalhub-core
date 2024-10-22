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
import io.argoproj.workflow.models.IoArgoprojEventsV1alpha1Amount;
import io.argoproj.workflow.models.IoArgoprojEventsV1alpha1Int64OrString;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;

/**
 * IoArgoprojEventsV1alpha1Backoff
 */

public class IoArgoprojEventsV1alpha1Backoff {
  public static final String SERIALIZED_NAME_DURATION = "duration";
  @SerializedName(SERIALIZED_NAME_DURATION)
  private IoArgoprojEventsV1alpha1Int64OrString duration;

  public static final String SERIALIZED_NAME_FACTOR = "factor";
  @SerializedName(SERIALIZED_NAME_FACTOR)
  private IoArgoprojEventsV1alpha1Amount factor;

  public static final String SERIALIZED_NAME_JITTER = "jitter";
  @SerializedName(SERIALIZED_NAME_JITTER)
  private IoArgoprojEventsV1alpha1Amount jitter;

  public static final String SERIALIZED_NAME_STEPS = "steps";
  @SerializedName(SERIALIZED_NAME_STEPS)
  private Integer steps;


  public IoArgoprojEventsV1alpha1Backoff duration(IoArgoprojEventsV1alpha1Int64OrString duration) {
    
    this.duration = duration;
    return this;
  }

   /**
   * Get duration
   * @return duration
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public IoArgoprojEventsV1alpha1Int64OrString getDuration() {
    return duration;
  }


  public void setDuration(IoArgoprojEventsV1alpha1Int64OrString duration) {
    this.duration = duration;
  }


  public IoArgoprojEventsV1alpha1Backoff factor(IoArgoprojEventsV1alpha1Amount factor) {
    
    this.factor = factor;
    return this;
  }

   /**
   * Get factor
   * @return factor
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public IoArgoprojEventsV1alpha1Amount getFactor() {
    return factor;
  }


  public void setFactor(IoArgoprojEventsV1alpha1Amount factor) {
    this.factor = factor;
  }


  public IoArgoprojEventsV1alpha1Backoff jitter(IoArgoprojEventsV1alpha1Amount jitter) {
    
    this.jitter = jitter;
    return this;
  }

   /**
   * Get jitter
   * @return jitter
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public IoArgoprojEventsV1alpha1Amount getJitter() {
    return jitter;
  }


  public void setJitter(IoArgoprojEventsV1alpha1Amount jitter) {
    this.jitter = jitter;
  }


  public IoArgoprojEventsV1alpha1Backoff steps(Integer steps) {
    
    this.steps = steps;
    return this;
  }

   /**
   * Get steps
   * @return steps
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Integer getSteps() {
    return steps;
  }


  public void setSteps(Integer steps) {
    this.steps = steps;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoArgoprojEventsV1alpha1Backoff ioArgoprojEventsV1alpha1Backoff = (IoArgoprojEventsV1alpha1Backoff) o;
    return Objects.equals(this.duration, ioArgoprojEventsV1alpha1Backoff.duration) &&
        Objects.equals(this.factor, ioArgoprojEventsV1alpha1Backoff.factor) &&
        Objects.equals(this.jitter, ioArgoprojEventsV1alpha1Backoff.jitter) &&
        Objects.equals(this.steps, ioArgoprojEventsV1alpha1Backoff.steps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(duration, factor, jitter, steps);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IoArgoprojEventsV1alpha1Backoff {\n");
    sb.append("    duration: ").append(toIndentedString(duration)).append("\n");
    sb.append("    factor: ").append(toIndentedString(factor)).append("\n");
    sb.append("    jitter: ").append(toIndentedString(jitter)).append("\n");
    sb.append("    steps: ").append(toIndentedString(steps)).append("\n");
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

