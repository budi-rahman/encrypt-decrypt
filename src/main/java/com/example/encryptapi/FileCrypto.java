package com.example.encryptapi;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Encrypts/decrypts a single file with AES-GCM, streaming so large files never have to
 * fit entirely in memory. Output format: [12-byte random IV][ciphertext][16-byte GCM tag].
 */
final class FileCrypto {

    private static final SecureRandom RNG = new SecureRandom();

    private FileCrypto() {}

    static void encryptFile(Path source, Path dest, byte[] key) throws IOException, GeneralSecurityException {
        byte[] iv = new byte[AppConfig.GCM_IV_BYTES];
        RNG.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AppConfig.ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(AppConfig.GCM_TAG_BITS, iv));

        Files.createDirectories(dest.getParent());
        try (InputStream in = Files.newInputStream(source);
             OutputStream rawOut = Files.newOutputStream(dest)) {
            rawOut.write(iv);
            try (CipherOutputStream cOut = new CipherOutputStream(rawOut, cipher)) {
                in.transferTo(cOut);
            }
        }
    }

    static void decryptFile(Path source, Path dest, byte[] key) throws IOException, GeneralSecurityException {
        try (InputStream rawIn = Files.newInputStream(source)) {
            byte[] iv = rawIn.readNBytes(AppConfig.GCM_IV_BYTES);
            if (iv.length != AppConfig.GCM_IV_BYTES) {
                throw new IOException("File too short to contain an IV: " + source);
            }
            Cipher cipher = Cipher.getInstance(AppConfig.ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AppConfig.GCM_TAG_BITS, iv));

            Files.createDirectories(dest.getParent());
            try (CipherInputStream cIn = new CipherInputStream(rawIn, cipher);
                 OutputStream out = Files.newOutputStream(dest)) {
                cIn.transferTo(out);
            }
        }
    }
}
