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
 * EventsourceLogEntry
 */

public class EventsourceLogEntry {
  public static final String SERIALIZED_NAME_EVENT_NAME = "eventName";
  @SerializedName(SERIALIZED_NAME_EVENT_NAME)
  private String eventName;

  public static final String SERIALIZED_NAME_EVENT_SOURCE_NAME = "eventSourceName";
  @SerializedName(SERIALIZED_NAME_EVENT_SOURCE_NAME)
  private String eventSourceName;

  public static final String SERIALIZED_NAME_EVENT_SOURCE_TYPE = "eventSourceType";
  @SerializedName(SERIALIZED_NAME_EVENT_SOURCE_TYPE)
  private String eventSourceType;

  public static final String SERIALIZED_NAME_LEVEL = "level";
  @SerializedName(SERIALIZED_NAME_LEVEL)
  private String level;

  public static final String SERIALIZED_NAME_MSG = "msg";
  @SerializedName(SERIALIZED_NAME_MSG)
  private String msg;

  public static final String SERIALIZED_NAME_NAMESPACE = "namespace";
  @SerializedName(SERIALIZED_NAME_NAMESPACE)
  private String namespace;

  public static final String SERIALIZED_NAME_TIME = "time";
  @SerializedName(SERIALIZED_NAME_TIME)
  private java.time.OffsetDateTime time;


  public EventsourceLogEntry eventName(String eventName) {
    
    this.eventName = eventName;
    return this;
  }

   /**
   * Get eventName
   * @return eventName
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getEventName() {
    return eventName;
  }


  public void setEventName(String eventName) {
    this.eventName = eventName;
  }


  public EventsourceLogEntry eventSourceName(String eventSourceName) {
    
    this.eventSourceName = eventSourceName;
    return this;
  }

   /**
   * Get eventSourceName
   * @return eventSourceName
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getEventSourceName() {
    return eventSourceName;
  }


  public void setEventSourceName(String eventSourceName) {
    this.eventSourceName = eventSourceName;
  }


  public EventsourceLogEntry eventSourceType(String eventSourceType) {
    
    this.eventSourceType = eventSourceType;
    return this;
  }

   /**
   * Get eventSourceType
   * @return eventSourceType
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getEventSourceType() {
    return eventSourceType;
  }


  public void setEventSourceType(String eventSourceType) {
    this.eventSourceType = eventSourceType;
  }


  public EventsourceLogEntry level(String level) {
    
    this.level = level;
    return this;
  }

   /**
   * Get level
   * @return level
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getLevel() {
    return level;
  }


  public void setLevel(String level) {
    this.level = level;
  }


  public EventsourceLogEntry msg(String msg) {
    
    this.msg = msg;
    return this;
  }

   /**
   * Get msg
   * @return msg
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getMsg() {
    return msg;
  }


  public void setMsg(String msg) {
    this.msg = msg;
  }


  public EventsourceLogEntry namespace(String namespace) {
    
    this.namespace = namespace;
    return this;
  }

   /**
   * Get namespace
   * @return namespace
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getNamespace() {
    return namespace;
  }


  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }


  public EventsourceLogEntry time(java.time.OffsetDateTime time) {
    
    this.time = time;
    return this;
  }

   /**
   * Get time
   * @return time
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public java.time.OffsetDateTime getTime() {
    return time;
  }


  public void setTime(java.time.OffsetDateTime time) {
    this.time = time;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EventsourceLogEntry eventsourceLogEntry = (EventsourceLogEntry) o;
    return Objects.equals(this.eventName, eventsourceLogEntry.eventName) &&
        Objects.equals(this.eventSourceName, eventsourceLogEntry.eventSourceName) &&
        Objects.equals(this.eventSourceType, eventsourceLogEntry.eventSourceType) &&
        Objects.equals(this.level, eventsourceLogEntry.level) &&
        Objects.equals(this.msg, eventsourceLogEntry.msg) &&
        Objects.equals(this.namespace, eventsourceLogEntry.namespace) &&
        Objects.equals(this.time, eventsourceLogEntry.time);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventName, eventSourceName, eventSourceType, level, msg, namespace, time);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EventsourceLogEntry {\n");
    sb.append("    eventName: ").append(toIndentedString(eventName)).append("\n");
    sb.append("    eventSourceName: ").append(toIndentedString(eventSourceName)).append("\n");
    sb.append("    eventSourceType: ").append(toIndentedString(eventSourceType)).append("\n");
    sb.append("    level: ").append(toIndentedString(level)).append("\n");
    sb.append("    msg: ").append(toIndentedString(msg)).append("\n");
    sb.append("    namespace: ").append(toIndentedString(namespace)).append("\n");
    sb.append("    time: ").append(toIndentedString(time)).append("\n");
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

