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
 * OSSLifecycleRule specifies how to manage bucket&#39;s lifecycle
 */
@ApiModel(description = "OSSLifecycleRule specifies how to manage bucket's lifecycle")

public class OSSLifecycleRule {
  public static final String SERIALIZED_NAME_MARK_DELETION_AFTER_DAYS = "markDeletionAfterDays";
  @SerializedName(SERIALIZED_NAME_MARK_DELETION_AFTER_DAYS)
  private Integer markDeletionAfterDays;

  public static final String SERIALIZED_NAME_MARK_INFREQUENT_ACCESS_AFTER_DAYS = "markInfrequentAccessAfterDays";
  @SerializedName(SERIALIZED_NAME_MARK_INFREQUENT_ACCESS_AFTER_DAYS)
  private Integer markInfrequentAccessAfterDays;


  public OSSLifecycleRule markDeletionAfterDays(Integer markDeletionAfterDays) {
    
    this.markDeletionAfterDays = markDeletionAfterDays;
    return this;
  }

   /**
   * MarkDeletionAfterDays is the number of days before we delete objects in the bucket
   * @return markDeletionAfterDays
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "MarkDeletionAfterDays is the number of days before we delete objects in the bucket")

  public Integer getMarkDeletionAfterDays() {
    return markDeletionAfterDays;
  }


  public void setMarkDeletionAfterDays(Integer markDeletionAfterDays) {
    this.markDeletionAfterDays = markDeletionAfterDays;
  }


  public OSSLifecycleRule markInfrequentAccessAfterDays(Integer markInfrequentAccessAfterDays) {
    
    this.markInfrequentAccessAfterDays = markInfrequentAccessAfterDays;
    return this;
  }

   /**
   * MarkInfrequentAccessAfterDays is the number of days before we convert the objects in the bucket to Infrequent Access (IA) storage type
   * @return markInfrequentAccessAfterDays
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "MarkInfrequentAccessAfterDays is the number of days before we convert the objects in the bucket to Infrequent Access (IA) storage type")

  public Integer getMarkInfrequentAccessAfterDays() {
    return markInfrequentAccessAfterDays;
  }


  public void setMarkInfrequentAccessAfterDays(Integer markInfrequentAccessAfterDays) {
    this.markInfrequentAccessAfterDays = markInfrequentAccessAfterDays;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OSSLifecycleRule osSLifecycleRule = (OSSLifecycleRule) o;
    return Objects.equals(this.markDeletionAfterDays, osSLifecycleRule.markDeletionAfterDays) &&
        Objects.equals(this.markInfrequentAccessAfterDays, osSLifecycleRule.markInfrequentAccessAfterDays);
  }

  @Override
  public int hashCode() {
    return Objects.hash(markDeletionAfterDays, markInfrequentAccessAfterDays);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OSSLifecycleRule {\n");
    sb.append("    markDeletionAfterDays: ").append(toIndentedString(markDeletionAfterDays)).append("\n");
    sb.append("    markInfrequentAccessAfterDays: ").append(toIndentedString(markInfrequentAccessAfterDays)).append("\n");
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

