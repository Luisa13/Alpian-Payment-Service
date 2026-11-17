package ch.example.template.transfer.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * TransferResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-11-17T09:52:58.884124+01:00[Europe/Zurich]", comments = "Generator version: 7.27.0")
public class TransferResponse {

  private UUID id;

  private String amount;

  private String currency;

  /**
   * Gets or Sets status
   */
  public enum StatusEnum {
    REQUESTED("REQUESTED"),
    
    SETTLED("SETTLED"),
    
    FAILED("FAILED");

    private final String value;

    StatusEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static StatusEnum fromValue(String value) {
      for (StatusEnum b : StatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private StatusEnum status;

  private @Nullable String failureReason;

  private UUID sourceAccountId;

  private UUID targetAccountId;

  private @Nullable String description;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime updatedAt;

  public TransferResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public TransferResponse(UUID id, String amount, String currency, StatusEnum status, UUID sourceAccountId, UUID targetAccountId, OffsetDateTime createdAt) {
    this.id = id;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.sourceAccountId = sourceAccountId;
    this.targetAccountId = targetAccountId;
    this.createdAt = createdAt;
  }

  public TransferResponse id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  @JsonProperty("id")
  public void setId(UUID id) {
    this.id = id;
  }

  public TransferResponse amount(String amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Get amount
   * @return amount
   */
  @NotNull 
  @JsonProperty("amount")
  public String getAmount() {
    return amount;
  }

  @JsonProperty("amount")
  public void setAmount(String amount) {
    this.amount = amount;
  }

  public TransferResponse currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * Get currency
   * @return currency
   */
  @NotNull 
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  @JsonProperty("currency")
  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public TransferResponse status(StatusEnum status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull 
  @JsonProperty("status")
  public StatusEnum getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(StatusEnum status) {
    this.status = status;
  }

  public TransferResponse failureReason(@Nullable String failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  /**
   * Get failureReason
   * @return failureReason
   */
  
  @JsonProperty("failureReason")
  public @Nullable String getFailureReason() {
    return failureReason;
  }

  @JsonProperty("failureReason")
  public void setFailureReason(@Nullable String failureReason) {
    this.failureReason = failureReason;
  }

  public TransferResponse sourceAccountId(UUID sourceAccountId) {
    this.sourceAccountId = sourceAccountId;
    return this;
  }

  /**
   * Get sourceAccountId
   * @return sourceAccountId
   */
  @NotNull @Valid 
  @JsonProperty("sourceAccountId")
  public UUID getSourceAccountId() {
    return sourceAccountId;
  }

  @JsonProperty("sourceAccountId")
  public void setSourceAccountId(UUID sourceAccountId) {
    this.sourceAccountId = sourceAccountId;
  }

  public TransferResponse targetAccountId(UUID targetAccountId) {
    this.targetAccountId = targetAccountId;
    return this;
  }

  /**
   * Get targetAccountId
   * @return targetAccountId
   */
  @NotNull @Valid 
  @JsonProperty("targetAccountId")
  public UUID getTargetAccountId() {
    return targetAccountId;
  }

  @JsonProperty("targetAccountId")
  public void setTargetAccountId(UUID targetAccountId) {
    this.targetAccountId = targetAccountId;
  }

  public TransferResponse description(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description
   * @return description
   */
  
  @JsonProperty("description")
  public @Nullable String getDescription() {
    return description;
  }

  @JsonProperty("description")
  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public TransferResponse createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @NotNull @Valid 
  @JsonProperty("createdAt")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  @JsonProperty("createdAt")
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public TransferResponse updatedAt(@Nullable OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
   */
  @Valid 
  @JsonProperty("updatedAt")
  public @Nullable OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  @JsonProperty("updatedAt")
  public void setUpdatedAt(@Nullable OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TransferResponse transferResponse = (TransferResponse) o;
    return Objects.equals(this.id, transferResponse.id) &&
        Objects.equals(this.amount, transferResponse.amount) &&
        Objects.equals(this.currency, transferResponse.currency) &&
        Objects.equals(this.status, transferResponse.status) &&
        Objects.equals(this.failureReason, transferResponse.failureReason) &&
        Objects.equals(this.sourceAccountId, transferResponse.sourceAccountId) &&
        Objects.equals(this.targetAccountId, transferResponse.targetAccountId) &&
        Objects.equals(this.description, transferResponse.description) &&
        Objects.equals(this.createdAt, transferResponse.createdAt) &&
        Objects.equals(this.updatedAt, transferResponse.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, amount, currency, status, failureReason, sourceAccountId, targetAccountId, description, createdAt, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TransferResponse {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    failureReason: ").append(toIndentedString(failureReason)).append("\n");
    sb.append("    sourceAccountId: ").append(toIndentedString(sourceAccountId)).append("\n");
    sb.append("    targetAccountId: ").append(toIndentedString(targetAccountId)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

