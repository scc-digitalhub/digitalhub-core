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
 * EndpointParam is for requesting optional fields that should be sent in the oauth request
 */
@ApiModel(description = "EndpointParam is for requesting optional fields that should be sent in the oauth request")

public class OAuth2EndpointParam {
  public static final String SERIALIZED_NAME_KEY = "key";
  @SerializedName(SERIALIZED_NAME_KEY)
  private String key;

  public static final String SERIALIZED_NAME_VALUE = "value";
  @SerializedName(SERIALIZED_NAME_VALUE)
  private String value;


  public OAuth2EndpointParam key(String key) {
    
    this.key = key;
    return this;
  }

   /**
   * Name is the header name
   * @return key
  **/
  @ApiModelProperty(required = true, value = "Name is the header name")

  public String getKey() {
    return key;
  }


  public void setKey(String key) {
    this.key = key;
  }


  public OAuth2EndpointParam value(String value) {
    
    this.value = value;
    return this;
  }

   /**
   * Value is the literal value to use for the header
   * @return value
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "Value is the literal value to use for the header")

  public String getValue() {
    return value;
  }


  public void setValue(String value) {
    this.value = value;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OAuth2EndpointParam oauth2EndpointParam = (OAuth2EndpointParam) o;
    return Objects.equals(this.key, oauth2EndpointParam.key) &&
        Objects.equals(this.value, oauth2EndpointParam.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OAuth2EndpointParam {\n");
    sb.append("    key: ").append(toIndentedString(key)).append("\n");
    sb.append("    value: ").append(toIndentedString(value)).append("\n");
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

