package triage.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private String orderId;
    private String email;
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String items;

    private BigDecimal amount;
    private OffsetDateTime createdAt;

    protected OrderEntity() {}

    public String getOrderId() { return orderId; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getItems() { return items; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
