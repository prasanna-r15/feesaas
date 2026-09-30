package com.feesaas.shared.tenancy;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base class for every tenant-owned entity.
 *  - tenant_id is stamped from the TenantContext on insert; a DTO can never choose it.
 *  - a Hibernate filter restricts every JPA query to the current tenant (layer 2, on top of RLS).
 *  - ids are time-ordered UUIDv7 for index locality.
 */
@MappedSuperclass
@FilterDef(name = TenantAwareTransactionManager.FILTER,
        parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = TenantAwareTransactionManager.FILTER, condition = "tenant_id = :tenantId")
public abstract class BaseTenantEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        UUID contextTenant = TenantContext.requireTenantId();
        if (tenantId != null && !tenantId.equals(contextTenant)) {
            throw new ApiException(ErrorCode.TENANT_MISMATCH, "Entity belongs to a different tenant.");
        }
        tenantId = contextTenant;
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BaseTenantEntity other
                && getClass().equals(other.getClass()) && id.equals(other.id));
    }

    @Override
    public int hashCode() { return Objects.hash(getClass(), id); }
}
