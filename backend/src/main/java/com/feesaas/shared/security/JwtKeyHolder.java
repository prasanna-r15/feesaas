package com.feesaas.shared.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RSA key used to sign and verify access JWTs (RS256).
 * Local/dev generates an ephemeral key at startup. Production should inject a stable key via env/KMS later.
 */
@Component
public class JwtKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyHolder.class);
    static final String KID = "local-1";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtKeyHolder() {
        KeyPair pair = generate();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        log.warn("Using an ephemeral RSA key for JWTs (kid={}). Restart invalidates access tokens.", KID);
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String kid() { return KID; }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }
}
