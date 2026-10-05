package com.example.encryptapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Loads (or, on first run, creates) the application's configuration file, including the
 * AES key used to encrypt/decrypt files in the watched folder.
 *
 * The key is generated once with SecureRandom and persisted in {@code app.properties} as
 * base64. Anyone who can read this file can decrypt every file this tool has encrypted,
 * so it must be protected with filesystem permissions at least as strong as the data itself
 * (see audit point 6 - security of backups must match the security of the source data).
 * A production deployment should prefer a KMS/HSM or DPAPI-protected store instead of a
 * plain file; this file-based key is what was asked for here.
 */
final class AppConfig {

    static final String ALGORITHM = "AES/GCM/NoPadding";
    static final int KEY_BITS = 256;
    static final int GCM_IV_BYTES = 12;
    static final int GCM_TAG_BITS = 128;
    static final String ENC_SUFFIX = ".enc";

    final Path encryptFolder;
    final Path originalsFolder;
    final byte[] aesKey;

    private AppConfig(Path encryptFolder, Path originalsFolder, byte[] aesKey) {
        this.encryptFolder = encryptFolder;
        this.originalsFolder = originalsFolder;
        this.aesKey = aesKey;
    }

    static AppConfig loadOrCreate(Path configFile) throws IOException {
        Properties props = new Properties();
        boolean isNew = !Files.exists(configFile);
        if (!isNew) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            }
        }

        String keyB64 = props.getProperty("aes.key.base64");
        if (keyB64 == null || keyB64.isBlank()) {
            byte[] generated = new byte[KEY_BITS / 8];
            new SecureRandom().nextBytes(generated);
            keyB64 = Base64.getEncoder().encodeToString(generated);
            props.setProperty("aes.key.base64", keyB64);
        }
        byte[] key = Base64.getDecoder().decode(keyB64);
        if (key.length * 8 != KEY_BITS) {
            throw new IllegalStateException("aes.key.base64 in " + configFile
                    + " is not a " + KEY_BITS + "-bit key");
        }

        String folder = props.getProperty("encrypt.folder");
        if (folder == null || folder.isBlank()) {
            // Fixed default per spec: the single folder E:\restore_test\encrypt is what gets watched,
            // regardless of where this jar/config happens to live.
            folder = "E:\\restore_test\\encrypt";
            props.setProperty("encrypt.folder", folder);
        }

        if (!props.containsKey("aes.algorithm")) {
            props.setProperty("aes.algorithm", ALGORITHM);
        }

        Files.createDirectories(configFile.getParent());
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "encrypt-cli configuration. aes.key.base64 is a secret: "
                    + "protect this file's permissions and never commit it to version control.");
        }

        Path encryptFolder = Path.of(folder);
        Path originalsFolder = encryptFolder.resolve("_originals");
        Files.createDirectories(encryptFolder);
        Files.createDirectories(originalsFolder);

        return new AppConfig(encryptFolder, originalsFolder, key);
    }
}
