package com.feesaas.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feesaas.shared.error.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TenantLogosTest {

    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=";

    @Test
    void acceptsPngUnderOneMegabyte() {
        String stored = TenantLogos.normalize("data:image/png;base64," + PNG_1X1);
        assertThat(stored).startsWith("data:image/png;base64,");
        byte[] decoded = Base64.getDecoder().decode(stored.substring(stored.indexOf(',') + 1));
        assertThat(decoded.length).isLessThanOrEqualTo(TenantLogos.MAX_BYTES);
    }

    @Test
    void rejectsDecodedPayloadOverOneMegabyte() {
        byte[] tooBig = new byte[TenantLogos.MAX_BYTES + 1];
        String raw = Base64.getEncoder().encodeToString(tooBig);
        assertThatThrownBy(() -> TenantLogos.normalize(raw))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 MB");
    }

    @Test
    void rejectsInvalidBase64() {
        assertThatThrownBy(() -> TenantLogos.normalize("not-base64!!!"))
                .isInstanceOf(ApiException.class);
    }
}
