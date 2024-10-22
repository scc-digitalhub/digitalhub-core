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
import io.argoproj.workflow.models.PersistentVolumeClaimTemplate;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;

/**
 * Represents an ephemeral volume that is handled by a normal storage driver.
 */
@ApiModel(description = "Represents an ephemeral volume that is handled by a normal storage driver.")

public class EphemeralVolumeSource {
  public static final String SERIALIZED_NAME_VOLUME_CLAIM_TEMPLATE = "volumeClaimTemplate";
  @SerializedName(SERIALIZED_NAME_VOLUME_CLAIM_TEMPLATE)
  private PersistentVolumeClaimTemplate volumeClaimTemplate;


  public EphemeralVolumeSource volumeClaimTemplate(PersistentVolumeClaimTemplate volumeClaimTemplate) {
    
    this.volumeClaimTemplate = volumeClaimTemplate;
    return this;
  }

   /**
   * Get volumeClaimTemplate
   * @return volumeClaimTemplate
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public PersistentVolumeClaimTemplate getVolumeClaimTemplate() {
    return volumeClaimTemplate;
  }


  public void setVolumeClaimTemplate(PersistentVolumeClaimTemplate volumeClaimTemplate) {
    this.volumeClaimTemplate = volumeClaimTemplate;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EphemeralVolumeSource ephemeralVolumeSource = (EphemeralVolumeSource) o;
    return Objects.equals(this.volumeClaimTemplate, ephemeralVolumeSource.volumeClaimTemplate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(volumeClaimTemplate);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EphemeralVolumeSource {\n");
    sb.append("    volumeClaimTemplate: ").append(toIndentedString(volumeClaimTemplate)).append("\n");
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

