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
import io.argoproj.workflow.models.ArtifactNodeSpec;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ArtifactGCSpec specifies the Artifacts that need to be deleted
 */
@ApiModel(description = "ArtifactGCSpec specifies the Artifacts that need to be deleted")

public class ArtifactGCSpec {
  public static final String SERIALIZED_NAME_ARTIFACTS_BY_NODE = "artifactsByNode";
  @SerializedName(SERIALIZED_NAME_ARTIFACTS_BY_NODE)
  private Map<String, ArtifactNodeSpec> artifactsByNode = null;


  public ArtifactGCSpec artifactsByNode(Map<String, ArtifactNodeSpec> artifactsByNode) {
    
    this.artifactsByNode = artifactsByNode;
    return this;
  }

  public ArtifactGCSpec putArtifactsByNodeItem(String key, ArtifactNodeSpec artifactsByNodeItem) {
    if (this.artifactsByNode == null) {
      this.artifactsByNode = new HashMap<String, ArtifactNodeSpec>();
    }
    this.artifactsByNode.put(key, artifactsByNodeItem);
    return this;
  }

   /**
   * ArtifactsByNode maps Node name to information pertaining to Artifacts on that Node
   * @return artifactsByNode
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "ArtifactsByNode maps Node name to information pertaining to Artifacts on that Node")

  public Map<String, ArtifactNodeSpec> getArtifactsByNode() {
    return artifactsByNode;
  }


  public void setArtifactsByNode(Map<String, ArtifactNodeSpec> artifactsByNode) {
    this.artifactsByNode = artifactsByNode;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactGCSpec artifactGCSpec = (ArtifactGCSpec) o;
    return Objects.equals(this.artifactsByNode, artifactGCSpec.artifactsByNode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactsByNode);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ArtifactGCSpec {\n");
    sb.append("    artifactsByNode: ").append(toIndentedString(artifactsByNode)).append("\n");
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

