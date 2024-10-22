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
 * IoArgoprojEventsV1alpha1ConfigMapPersistence
 */

public class IoArgoprojEventsV1alpha1ConfigMapPersistence {
  public static final String SERIALIZED_NAME_CREATE_IF_NOT_EXIST = "createIfNotExist";
  @SerializedName(SERIALIZED_NAME_CREATE_IF_NOT_EXIST)
  private Boolean createIfNotExist;

  public static final String SERIALIZED_NAME_NAME = "name";
  @SerializedName(SERIALIZED_NAME_NAME)
  private String name;


  public IoArgoprojEventsV1alpha1ConfigMapPersistence createIfNotExist(Boolean createIfNotExist) {
    
    this.createIfNotExist = createIfNotExist;
    return this;
  }

   /**
   * Get createIfNotExist
   * @return createIfNotExist
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getCreateIfNotExist() {
    return createIfNotExist;
  }


  public void setCreateIfNotExist(Boolean createIfNotExist) {
    this.createIfNotExist = createIfNotExist;
  }


  public IoArgoprojEventsV1alpha1ConfigMapPersistence name(String name) {
    
    this.name = name;
    return this;
  }

   /**
   * Get name
   * @return name
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoArgoprojEventsV1alpha1ConfigMapPersistence ioArgoprojEventsV1alpha1ConfigMapPersistence = (IoArgoprojEventsV1alpha1ConfigMapPersistence) o;
    return Objects.equals(this.createIfNotExist, ioArgoprojEventsV1alpha1ConfigMapPersistence.createIfNotExist) &&
        Objects.equals(this.name, ioArgoprojEventsV1alpha1ConfigMapPersistence.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createIfNotExist, name);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IoArgoprojEventsV1alpha1ConfigMapPersistence {\n");
    sb.append("    createIfNotExist: ").append(toIndentedString(createIfNotExist)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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

