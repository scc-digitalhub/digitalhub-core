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
import io.argoproj.workflow.models.ServicePort;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * IoArgoprojEventsV1alpha1Service
 */

public class IoArgoprojEventsV1alpha1Service {
  public static final String SERIALIZED_NAME_CLUSTER_I_P = "clusterIP";
  @SerializedName(SERIALIZED_NAME_CLUSTER_I_P)
  private String clusterIP;

  public static final String SERIALIZED_NAME_PORTS = "ports";
  @SerializedName(SERIALIZED_NAME_PORTS)
  private List<ServicePort> ports = null;


  public IoArgoprojEventsV1alpha1Service clusterIP(String clusterIP) {
    
    this.clusterIP = clusterIP;
    return this;
  }

   /**
   * Get clusterIP
   * @return clusterIP
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getClusterIP() {
    return clusterIP;
  }


  public void setClusterIP(String clusterIP) {
    this.clusterIP = clusterIP;
  }


  public IoArgoprojEventsV1alpha1Service ports(List<ServicePort> ports) {
    
    this.ports = ports;
    return this;
  }

  public IoArgoprojEventsV1alpha1Service addPortsItem(ServicePort portsItem) {
    if (this.ports == null) {
      this.ports = new ArrayList<ServicePort>();
    }
    this.ports.add(portsItem);
    return this;
  }

   /**
   * Get ports
   * @return ports
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public List<ServicePort> getPorts() {
    return ports;
  }


  public void setPorts(List<ServicePort> ports) {
    this.ports = ports;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoArgoprojEventsV1alpha1Service ioArgoprojEventsV1alpha1Service = (IoArgoprojEventsV1alpha1Service) o;
    return Objects.equals(this.clusterIP, ioArgoprojEventsV1alpha1Service.clusterIP) &&
        Objects.equals(this.ports, ioArgoprojEventsV1alpha1Service.ports);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clusterIP, ports);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IoArgoprojEventsV1alpha1Service {\n");
    sb.append("    clusterIP: ").append(toIndentedString(clusterIP)).append("\n");
    sb.append("    ports: ").append(toIndentedString(ports)).append("\n");
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

