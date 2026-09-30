package com.feesaas.user.application;

import java.util.UUID;

public interface OwnerProvisioner {
    UUID createOwner(UUID tenantId, String email, String phone, String fullName, String passwordHash);
}
