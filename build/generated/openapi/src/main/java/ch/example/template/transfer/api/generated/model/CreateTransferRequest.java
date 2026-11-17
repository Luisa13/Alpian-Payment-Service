package ch.example.template.transfer.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * CreateTransferRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-11-17T16:04:41.836084+01:00[Europe/Zurich]", comments = "Generator version: 7.27.0")
public class CreateTransferRequest {

  private String amount;

  private String currency;

  private UUID sourceAccountId;

  private UUID targetAccountId;

  private @Nullable String description;

  public CreateTransferRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateTransferRequest(String amount, String currency, UUID sourceAccountId, UUID targetAccountId) {
    this.amount = amount;
    this.currency = currency;
    this.sourceAccountId = sourceAccountId;
    this.targetAccountId = targetAccountId;
  }

  public CreateTransferRequest amount(String amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Decimal amount as a string to preserve precision. No scientific notation. Max 2 decimal places for most currencies. 
   * @return amount
   */
  @NotNull @Pattern(regexp = "^\\d+(\\.\\d{1,8})?$") 
  @JsonProperty("amount")
  public String getAmount() {
    return amount;
  }

  @JsonProperty("amount")
  public void setAmount(String amount) {
    this.amount = amount;
  }

  public CreateTransferRequest currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * ISO 4217 currency code
   * @return currency
   */
  @NotNull @Pattern(regexp = "^[A-Z]{3}$") @Size(min = 3, max = 3) 
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  @JsonProperty("currency")
  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public CreateTransferRequest sourceAccountId(UUID sourceAccountId) {
    this.sourceAccountId = sourceAccountId;
    return this;
  }

  /**
   * Account to debit
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

  public CreateTransferRequest targetAccountId(UUID targetAccountId) {
    this.targetAccountId = targetAccountId;
    return this;
  }

  /**
   * Account to credit
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

  public CreateTransferRequest description(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * Human-readable purpose
   * @return description
   */
  @Size(max = 255) 
  @JsonProperty("description")
  public @Nullable String getDescription() {
    return description;
  }

  @JsonProperty("description")
  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateTransferRequest createTransferRequest = (CreateTransferRequest) o;
    return Objects.equals(this.amount, createTransferRequest.amount) &&
        Objects.equals(this.currency, createTransferRequest.currency) &&
        Objects.equals(this.sourceAccountId, createTransferRequest.sourceAccountId) &&
        Objects.equals(this.targetAccountId, createTransferRequest.targetAccountId) &&
        Objects.equals(this.description, createTransferRequest.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, currency, sourceAccountId, targetAccountId, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateTransferRequest {\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
    sb.append("    sourceAccountId: ").append(toIndentedString(sourceAccountId)).append("\n");
    sb.append("    targetAccountId: ").append(toIndentedString(targetAccountId)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
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

