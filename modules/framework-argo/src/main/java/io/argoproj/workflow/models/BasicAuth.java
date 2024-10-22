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
 * BasicAuth describes the secret selectors required for basic authentication
 */
@ApiModel(description = "BasicAuth describes the secret selectors required for basic authentication")

public class BasicAuth {
  public static final String SERIALIZED_NAME_PASSWORD_SECRET = "passwordSecret";
  @SerializedName(SERIALIZED_NAME_PASSWORD_SECRET)
  private io.kubernetes.client.openapi.models.V1SecretKeySelector passwordSecret;

  public static final String SERIALIZED_NAME_USERNAME_SECRET = "usernameSecret";
  @SerializedName(SERIALIZED_NAME_USERNAME_SECRET)
  private io.kubernetes.client.openapi.models.V1SecretKeySelector usernameSecret;


  public BasicAuth passwordSecret(io.kubernetes.client.openapi.models.V1SecretKeySelector passwordSecret) {
    
    this.passwordSecret = passwordSecret;
    return this;
  }

   /**
   * Get passwordSecret
   * @return passwordSecret
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public io.kubernetes.client.openapi.models.V1SecretKeySelector getPasswordSecret() {
    return passwordSecret;
  }


  public void setPasswordSecret(io.kubernetes.client.openapi.models.V1SecretKeySelector passwordSecret) {
    this.passwordSecret = passwordSecret;
  }


  public BasicAuth usernameSecret(io.kubernetes.client.openapi.models.V1SecretKeySelector usernameSecret) {
    
    this.usernameSecret = usernameSecret;
    return this;
  }

   /**
   * Get usernameSecret
   * @return usernameSecret
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public io.kubernetes.client.openapi.models.V1SecretKeySelector getUsernameSecret() {
    return usernameSecret;
  }


  public void setUsernameSecret(io.kubernetes.client.openapi.models.V1SecretKeySelector usernameSecret) {
    this.usernameSecret = usernameSecret;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BasicAuth basicAuth = (BasicAuth) o;
    return Objects.equals(this.passwordSecret, basicAuth.passwordSecret) &&
        Objects.equals(this.usernameSecret, basicAuth.usernameSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(passwordSecret, usernameSecret);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BasicAuth {\n");
    sb.append("    passwordSecret: ").append(toIndentedString(passwordSecret)).append("\n");
    sb.append("    usernameSecret: ").append(toIndentedString(usernameSecret)).append("\n");
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

