package com.feesaas.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantSlugsTest {

    @Test
    void turnsBusinessNameIntoSlug() {
        assertThat(TenantSlugs.normalize("Iron man Unisex Gym")).isEqualTo("iron-man-unisex-gym");
    }

    @Test
    void keepsValidSlugs() {
        assertThat(TenantSlugs.normalize("fit-zone")).isEqualTo("fit-zone");
    }
}
