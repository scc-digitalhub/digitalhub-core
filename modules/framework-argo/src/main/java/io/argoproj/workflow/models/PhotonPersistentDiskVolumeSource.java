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
 * Represents a Photon Controller persistent disk resource.
 */
@ApiModel(description = "Represents a Photon Controller persistent disk resource.")

public class PhotonPersistentDiskVolumeSource {
  public static final String SERIALIZED_NAME_FS_TYPE = "fsType";
  @SerializedName(SERIALIZED_NAME_FS_TYPE)
  private String fsType;

  public static final String SERIALIZED_NAME_PD_I_D = "pdID";
  @SerializedName(SERIALIZED_NAME_PD_I_D)
  private String pdID;


  public PhotonPersistentDiskVolumeSource fsType(String fsType) {
    
    this.fsType = fsType;
    return this;
  }

   /**
   * Filesystem type to mount. Must be a filesystem type supported by the host operating system. Ex. \&quot;ext4\&quot;, \&quot;xfs\&quot;, \&quot;ntfs\&quot;. Implicitly inferred to be \&quot;ext4\&quot; if unspecified.
   * @return fsType
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "Filesystem type to mount. Must be a filesystem type supported by the host operating system. Ex. \"ext4\", \"xfs\", \"ntfs\". Implicitly inferred to be \"ext4\" if unspecified.")

  public String getFsType() {
    return fsType;
  }


  public void setFsType(String fsType) {
    this.fsType = fsType;
  }


  public PhotonPersistentDiskVolumeSource pdID(String pdID) {
    
    this.pdID = pdID;
    return this;
  }

   /**
   * ID that identifies Photon Controller persistent disk
   * @return pdID
  **/
  @ApiModelProperty(required = true, value = "ID that identifies Photon Controller persistent disk")

  public String getPdID() {
    return pdID;
  }


  public void setPdID(String pdID) {
    this.pdID = pdID;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PhotonPersistentDiskVolumeSource photonPersistentDiskVolumeSource = (PhotonPersistentDiskVolumeSource) o;
    return Objects.equals(this.fsType, photonPersistentDiskVolumeSource.fsType) &&
        Objects.equals(this.pdID, photonPersistentDiskVolumeSource.pdID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fsType, pdID);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PhotonPersistentDiskVolumeSource {\n");
    sb.append("    fsType: ").append(toIndentedString(fsType)).append("\n");
    sb.append("    pdID: ").append(toIndentedString(pdID)).append("\n");
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

