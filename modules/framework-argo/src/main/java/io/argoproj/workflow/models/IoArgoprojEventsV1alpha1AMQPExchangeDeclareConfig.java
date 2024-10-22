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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;

/**
 * IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig
 */

public class IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig {
  public static final String SERIALIZED_NAME_AUTO_DELETE = "autoDelete";
  @SerializedName(SERIALIZED_NAME_AUTO_DELETE)
  private Boolean autoDelete;

  public static final String SERIALIZED_NAME_DURABLE = "durable";
  @SerializedName(SERIALIZED_NAME_DURABLE)
  private Boolean durable;

  public static final String SERIALIZED_NAME_INTERNAL = "internal";
  @SerializedName(SERIALIZED_NAME_INTERNAL)
  private Boolean internal;

  public static final String SERIALIZED_NAME_NO_WAIT = "noWait";
  @SerializedName(SERIALIZED_NAME_NO_WAIT)
  private Boolean noWait;


  public IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig autoDelete(Boolean autoDelete) {
    
    this.autoDelete = autoDelete;
    return this;
  }

   /**
   * Get autoDelete
   * @return autoDelete
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getAutoDelete() {
    return autoDelete;
  }


  public void setAutoDelete(Boolean autoDelete) {
    this.autoDelete = autoDelete;
  }


  public IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig durable(Boolean durable) {
    
    this.durable = durable;
    return this;
  }

   /**
   * Get durable
   * @return durable
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getDurable() {
    return durable;
  }


  public void setDurable(Boolean durable) {
    this.durable = durable;
  }


  public IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig internal(Boolean internal) {
    
    this.internal = internal;
    return this;
  }

   /**
   * Get internal
   * @return internal
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getInternal() {
    return internal;
  }


  public void setInternal(Boolean internal) {
    this.internal = internal;
  }


  public IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig noWait(Boolean noWait) {
    
    this.noWait = noWait;
    return this;
  }

   /**
   * Get noWait
   * @return noWait
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getNoWait() {
    return noWait;
  }


  public void setNoWait(Boolean noWait) {
    this.noWait = noWait;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig ioArgoprojEventsV1alpha1AMQPExchangeDeclareConfig = (IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig) o;
    return Objects.equals(this.autoDelete, ioArgoprojEventsV1alpha1AMQPExchangeDeclareConfig.autoDelete) &&
        Objects.equals(this.durable, ioArgoprojEventsV1alpha1AMQPExchangeDeclareConfig.durable) &&
        Objects.equals(this.internal, ioArgoprojEventsV1alpha1AMQPExchangeDeclareConfig.internal) &&
        Objects.equals(this.noWait, ioArgoprojEventsV1alpha1AMQPExchangeDeclareConfig.noWait);
  }

  @Override
  public int hashCode() {
    return Objects.hash(autoDelete, durable, internal, noWait);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IoArgoprojEventsV1alpha1AMQPExchangeDeclareConfig {\n");
    sb.append("    autoDelete: ").append(toIndentedString(autoDelete)).append("\n");
    sb.append("    durable: ").append(toIndentedString(durable)).append("\n");
    sb.append("    internal: ").append(toIndentedString(internal)).append("\n");
    sb.append("    noWait: ").append(toIndentedString(noWait)).append("\n");
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

