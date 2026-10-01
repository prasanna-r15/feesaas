package com.feesaas.shared;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantScope;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void platformAdminHasNoTenantId() {
        TenantContext.callAs(TenantScope.platformAdmin(), () -> {
            assertThatThrownBy(TenantContext::requireTenantId)
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No tenant in context");
            return null;
        });
    }
}
