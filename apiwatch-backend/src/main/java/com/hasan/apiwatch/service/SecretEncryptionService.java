package com.hasan.apiwatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION_PREFIX = "v1:";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(
            @Value("${apiwatch.secrets.encryption-key}") String encodedKey
    ) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APIWatch encryption key must be valid Base64", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("APIWatch encryption key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt service secret", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!encryptedValue.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted secret version");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(
                    encryptedValue.substring(VERSION_PREFIX.length())
            );
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted secret payload is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt service secret", exception);
        }
    }
}
