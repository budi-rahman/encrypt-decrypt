package com.example.encryptapi;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * CLI that AES-256-GCM encrypts (or decrypts) every file directly inside the watched
 * folder (config: encrypt.folder, default E:\restore_test\encrypt). The AES key lives in
 * a config file (config/app.properties, created with a fresh random key on first run).
 *
 * Usage:
 *   java -jar encrypt-cli.jar encrypt [configFile]   - encrypts every plaintext file in the
 *                                                       folder; each becomes "<name>.enc" and
 *                                                       the original is moved to _originals/
 *   java -jar encrypt-cli.jar decrypt [configFile]   - decrypts every "*.enc" file back to
 *                                                       "<name>" in the same folder
 *   java -jar encrypt-cli.jar status  [configFile]   - lists pending/encrypted files, no changes
 *
 * configFile defaults to "config/app.properties" relative to the current directory.
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            usageAndExit();
        }
        String command = args[0];
        Path configFile = Path.of(args.length > 1 ? args[1] : "config/app.properties").toAbsolutePath();

        try {
            AppConfig config = AppConfig.loadOrCreate(configFile);
            switch (command) {
                case "encrypt" -> runEncrypt(config);
                case "decrypt" -> runDecrypt(config);
                case "status" -> runStatus(config);
                default -> usageAndExit();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usageAndExit() {
        System.err.println("Usage: java -jar encrypt-cli.jar <encrypt|decrypt|status> [configFile]");
        System.err.println("  configFile defaults to config/app.properties");
        System.exit(1);
    }

    private static void runEncrypt(AppConfig config) throws Exception {
        System.out.println("Watched folder: " + config.encryptFolder);
        int ok = 0, failed = 0, skipped = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.encryptFolder)) {
            for (Path source : stream) {
                if (Files.isDirectory(source)) continue;
                String name = source.getFileName().toString();
                if (name.endsWith(AppConfig.ENC_SUFFIX)) {
                    System.out.println("SKIP      " + name + " (already encrypted)");
                    skipped++;
                    continue;
                }

                Path dest = config.encryptFolder.resolve(name + AppConfig.ENC_SUFFIX);
                try {
                    FileCrypto.encryptFile(source, dest, config.aesKey);
                    Path movedOriginal = config.originalsFolder.resolve(name);
                    Files.move(source, movedOriginal, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("ENCRYPTED " + name + " -> " + dest.getFileName()
                            + " (original moved to " + config.originalsFolder.relativize(movedOriginal) + ")");
                    ok++;
                } catch (Exception e) {
                    System.out.println("FAILED    " + name + ": " + e.getMessage());
                    Files.deleteIfExists(dest);
                    failed++;
                }
            }
        }
        System.out.printf("Done. encrypted=%d failed=%d skipped=%d%n", ok, failed, skipped);
        if (failed > 0) System.exit(1);
    }

    private static void runDecrypt(AppConfig config) throws Exception {
        System.out.println("Watched folder: " + config.encryptFolder);
        int ok = 0, failed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.encryptFolder, "*" + AppConfig.ENC_SUFFIX)) {
            for (Path source : stream) {
                if (Files.isDirectory(source)) continue;
                String name = source.getFileName().toString();
                String plainName = name.substring(0, name.length() - AppConfig.ENC_SUFFIX.length());
                Path dest = config.encryptFolder.resolve(plainName);
                try {
                    FileCrypto.decryptFile(source, dest, config.aesKey);
                    System.out.println("DECRYPTED " + name + " -> " + plainName);
                    ok++;
                } catch (Exception e) {
                    System.out.println("FAILED    " + name + ": " + e.getMessage());
                    Files.deleteIfExists(dest);
                    failed++;
                }
            }
        }
        System.out.printf("Done. decrypted=%d failed=%d%n", ok, failed);
        if (failed > 0) System.exit(1);
    }

    private static void runStatus(AppConfig config) throws Exception {
        System.out.println("Watched folder   : " + config.encryptFolder);
        System.out.println("Originals folder : " + config.originalsFolder);
        System.out.println("Algorithm        : " + AppConfig.ALGORITHM + " (" + AppConfig.KEY_BITS + "-bit key)");
        System.out.println("Key SHA-256      : " + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(config.aesKey)));

        int pending = 0, encrypted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.encryptFolder)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) continue;
                String name = p.getFileName().toString();
                if (name.endsWith(AppConfig.ENC_SUFFIX)) {
                    System.out.println("  [encrypted] " + name);
                    encrypted++;
                } else {
                    System.out.println("  [pending]   " + name);
                    pending++;
                }
            }
        }
        System.out.printf("Total: %d pending, %d encrypted%n", pending, encrypted);
    }
}
