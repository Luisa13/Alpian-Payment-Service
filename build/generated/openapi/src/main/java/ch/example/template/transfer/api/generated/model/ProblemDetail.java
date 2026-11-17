package ch.example.template.transfer.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.net.URI;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ProblemDetail
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-11-17T16:04:41.836084+01:00[Europe/Zurich]", comments = "Generator version: 7.27.0")
public class ProblemDetail {

  private @Nullable URI type;

  private @Nullable String title;

  private @Nullable Integer status;

  private @Nullable String detail;

  private @Nullable URI instance;

  private @Nullable String errorReference;

  public ProblemDetail type(@Nullable URI type) {
    this.type = type;
    return this;
  }

  /**
   * URI identifying the problem type
   * @return type
   */
  @Valid 
  @JsonProperty("type")
  public @Nullable URI getType() {
    return type;
  }

  @JsonProperty("type")
  public void setType(@Nullable URI type) {
    this.type = type;
  }

  public ProblemDetail title(@Nullable String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  
  @JsonProperty("title")
  public @Nullable String getTitle() {
    return title;
  }

  @JsonProperty("title")
  public void setTitle(@Nullable String title) {
    this.title = title;
  }

  public ProblemDetail status(@Nullable Integer status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  
  @JsonProperty("status")
  public @Nullable Integer getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(@Nullable Integer status) {
    this.status = status;
  }

  public ProblemDetail detail(@Nullable String detail) {
    this.detail = detail;
    return this;
  }

  /**
   * Get detail
   * @return detail
   */
  
  @JsonProperty("detail")
  public @Nullable String getDetail() {
    return detail;
  }

  @JsonProperty("detail")
  public void setDetail(@Nullable String detail) {
    this.detail = detail;
  }

  public ProblemDetail instance(@Nullable URI instance) {
    this.instance = instance;
    return this;
  }

  /**
   * Get instance
   * @return instance
   */
  @Valid 
  @JsonProperty("instance")
  public @Nullable URI getInstance() {
    return instance;
  }

  @JsonProperty("instance")
  public void setInstance(@Nullable URI instance) {
    this.instance = instance;
  }

  public ProblemDetail errorReference(@Nullable String errorReference) {
    this.errorReference = errorReference;
    return this;
  }

  /**
   * Opaque reference for server-side log correlation (500 only)
   * @return errorReference
   */
  
  @JsonProperty("errorReference")
  public @Nullable String getErrorReference() {
    return errorReference;
  }

  @JsonProperty("errorReference")
  public void setErrorReference(@Nullable String errorReference) {
    this.errorReference = errorReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProblemDetail problemDetail = (ProblemDetail) o;
    return Objects.equals(this.type, problemDetail.type) &&
        Objects.equals(this.title, problemDetail.title) &&
        Objects.equals(this.status, problemDetail.status) &&
        Objects.equals(this.detail, problemDetail.detail) &&
        Objects.equals(this.instance, problemDetail.instance) &&
        Objects.equals(this.errorReference, problemDetail.errorReference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, title, status, detail, instance, errorReference);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProblemDetail {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
    sb.append("    instance: ").append(toIndentedString(instance)).append("\n");
    sb.append("    errorReference: ").append(toIndentedString(errorReference)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

