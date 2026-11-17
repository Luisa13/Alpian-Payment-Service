package ch.example.template.transfer.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ValidationProblemDetail
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-11-17T09:52:58.884124+01:00[Europe/Zurich]", comments = "Generator version: 7.27.0")
public class ValidationProblemDetail {

  private @Nullable URI type;

  private @Nullable String title;

  private @Nullable Integer status;

  private @Nullable String detail;

  private @Nullable URI instance;

  private @Nullable String errorReference;

  private Map<String, String> violations = new HashMap<>();

  public ValidationProblemDetail type(@Nullable URI type) {
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

  public ValidationProblemDetail title(@Nullable String title) {
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

  public ValidationProblemDetail status(@Nullable Integer status) {
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

  public ValidationProblemDetail detail(@Nullable String detail) {
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

  public ValidationProblemDetail instance(@Nullable URI instance) {
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

  public ValidationProblemDetail errorReference(@Nullable String errorReference) {
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

  public ValidationProblemDetail violations(Map<String, String> violations) {
    this.violations = violations;
    return this;
  }

  public ValidationProblemDetail putViolationsItem(String key, String violationsItem) {
    if (this.violations == null) {
      this.violations = new HashMap<>();
    }
    this.violations.put(key, violationsItem);
    return this;
  }

  /**
   * Map of field path → error message
   * @return violations
   */
  
  @JsonProperty("violations")
  public Map<String, String> getViolations() {
    return violations;
  }

  @JsonProperty("violations")
  public void setViolations(Map<String, String> violations) {
    this.violations = violations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValidationProblemDetail validationProblemDetail = (ValidationProblemDetail) o;
    return Objects.equals(this.type, validationProblemDetail.type) &&
        Objects.equals(this.title, validationProblemDetail.title) &&
        Objects.equals(this.status, validationProblemDetail.status) &&
        Objects.equals(this.detail, validationProblemDetail.detail) &&
        Objects.equals(this.instance, validationProblemDetail.instance) &&
        Objects.equals(this.errorReference, validationProblemDetail.errorReference) &&
        Objects.equals(this.violations, validationProblemDetail.violations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, title, status, detail, instance, errorReference, violations);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ValidationProblemDetail {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
    sb.append("    instance: ").append(toIndentedString(instance)).append("\n");
    sb.append("    errorReference: ").append(toIndentedString(errorReference)).append("\n");
    sb.append("    violations: ").append(toIndentedString(violations)).append("\n");
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

