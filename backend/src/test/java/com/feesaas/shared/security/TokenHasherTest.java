package com.feesaas.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void hashesAreDeterministicAndTokensAreOpaque() {
        String token = TokenHasher.randomUrlToken();
        assertThat(token).hasSizeGreaterThan(32);
        assertThat(TokenHasher.sha256(token)).isEqualTo(TokenHasher.sha256(token));
        assertThat(TokenHasher.sha256(token)).isNotEqualTo(token);
        assertThat(TokenHasher.sha256("a")).isNotEqualTo(TokenHasher.sha256("b"));
    }
}
