package com.nuuly.inventory.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class JwkRotationService {

    // Overlap window: keep retired keys for 20 min (> 10 min token TTL)
    private static final int MAX_KEYS = 3;

    private final List<RSAKey> keys = new ArrayList<>();

    public JwkRotationService() {
        // Generate initial signing key on startup
        keys.add(generateKey());
    }

    public List<RSAKey> currentJwks() {
        synchronized (keys) {
            return Collections.unmodifiableList(new ArrayList<>(keys));
        }
    }

    public RSAKey activeKey() {
        synchronized (keys) {
            return keys.get(0);
        }
    }

    @Scheduled(initialDelayString = "PT30M", fixedRateString = "PT30M")
    public void rotate() {
        synchronized (keys) {
            keys.add(0, generateKey());
            // Drop keys beyond overlap window
            while (keys.size() > MAX_KEYS) {
                keys.remove(keys.size() - 1);
            }
        }
    }

    private RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key", e);
        }
    }
}
