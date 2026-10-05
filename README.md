# encrypt-cli

CLI sederhana yang meng-enkripsi (dan bisa men-dekripsi kembali) semua file yang ada
langsung di dalam folder `E:\restore_test\encrypt`, memakai AES-256-GCM. Kunci AES
disimpan di sebuah file konfigurasi lokal.

Tidak ada API/HTTP server, tidak ada dependensi pihak ketiga — hanya `javax.crypto`
bawaan JDK. Ini murni alat baris perintah (CLI) yang dijalankan manual atau lewat
scheduler (Task Scheduler / cron), bukan layanan yang terus menyala.

## Cara pakai

```bash
cd E:\restore_test\encrypt-cli
java -jar target/encrypt-cli.jar encrypt   # enkripsi semua file baru di folder encrypt
java -jar target/encrypt-cli.jar decrypt   # dekripsi semua file *.enc di folder encrypt
java -jar target/encrypt-cli.jar status    # tampilkan daftar file, tidak mengubah apa pun
```

Build ulang setelah mengubah kode:

```bash
mvn -q package
```

Jar hasil build ada di `target/encrypt-cli.jar`.

### `encrypt`
- Memproses setiap file yang ada **langsung** di `E:\restore_test\encrypt` (tidak masuk
  ke subfolder).
- File yang sudah berakhiran `.enc` dilewati (tidak dienkripsi ulang).
- Untuk tiap file `nama.ext`:
  1. Dibuat `nama.ext.enc` (hasil enkripsi) di folder yang sama.
  2. File asli **dipindah** (bukan dihapus) ke `encrypt\_originals\nama.ext`.
- Kalau satu file gagal dienkripsi, file lain tetap diproses; proses berhenti dengan
  exit code 1 di akhir kalau ada yang gagal.

### `decrypt`
- Memproses setiap file `*.enc` di `E:\restore_test\encrypt`.
- Untuk `nama.ext.enc`, hasil dekripsinya ditulis sebagai `nama.ext` di folder yang sama
  (menimpa kalau sudah ada). File `.enc` sumber tidak dihapus.
- Kalau file rusak atau kunci tidak cocok, dekripsi file itu gagal dengan pesan error
  (lihat "Deteksi kerusakan/pemalsuan" di bawah) dan file lain tetap diproses.

### `status`
- Hanya menampilkan: folder yang dipantau, fingerprint SHA-256 dari kunci (bukan
  kuncinya sendiri), dan daftar file `[pending]` (belum dienkripsi) vs `[encrypted]`.
- Tidak mengubah file apa pun.

## Konfigurasi

File: `E:\restore_test\encrypt-cli\config\app.properties`. Dibuat otomatis dengan kunci
AES baru (acak) saat pertama kali dijalankan kalau belum ada.

```properties
aes.algorithm=AES/GCM/NoPadding
aes.key.base64=<kunci AES-256, base64, 44 karakter>
encrypt.folder=E:\\restore_test\\encrypt
```

- `aes.key.base64` — **kunci rahasia**. Siapa pun yang membaca nilai ini bisa
  mendekripsi semua file yang pernah dienkripsi CLI ini.
- `encrypt.folder` — folder yang diproses. Default tetap `E:\restore_test\encrypt`
  walau jar-nya dijalankan dari folder lain.

Bisa dipakai lebih dari satu file config (misalnya untuk kunci/folder berbeda) dengan
memberi path sebagai argumen kedua:

```bash
java -jar target/encrypt-cli.jar encrypt config/app-lain.properties
```

## Format file terenkripsi

```
[12 byte IV acak][ciphertext][16 byte GCM authentication tag]
```

- IV dibuat baru secara acak untuk **setiap file**, jadi dua file dengan isi sama akan
  menghasilkan `.enc` yang berbeda.
- Enkripsi/dekripsi memakai stream (`CipherInputStream`/`CipherOutputStream`), jadi file
  besar tidak perlu dimuat penuh ke memori.

## Deteksi kerusakan/pemalsuan

AES-GCM adalah mode *authenticated encryption*: kalau file `.enc` diubah walau cuma satu
byte, `decrypt` akan gagal dengan `javax.crypto.AEADBadTagException: Tag mismatch` alih-
alih diam-diam menghasilkan data rusak. Ini sudah diuji manual: mengubah 1 byte terakhir
sebuah file `.enc` membuat proses decrypt-nya gagal dan file lain tetap diproses normal.

## Catatan keamanan (penting untuk audit)

- **Kunci disimpan polos** (base64, tidak dienkripsi lagi) di `config/app.properties`.
  Ini sesuai permintaan awal ("kuncinya disimpan dalam suatu file konfig"), tapi
  konsekuensinya: keamanan seluruh data yang dienkripsi CLI ini bergantung penuh pada
  siapa saja yang bisa membaca file config itu.
- Minimal yang harus dilakukan sebelum dipakai untuk data produksi/backup sungguhan:
  - Batasi izin baca `config/app.properties` hanya untuk akun yang menjalankan CLI ini
    (`icacls` di Windows).
  - **Jangan pernah** commit `config/app.properties` ke Git atau sistem version control
    lain.
  - Simpan salinan cadangan kunci di tempat terpisah dan aman — kalau file config hilang,
    semua file `.enc` yang sudah dibuat **tidak bisa didekripsi lagi**.
- Untuk kebutuhan produksi jangka panjang, pertimbangkan pindah dari file polos ke KMS/
  HSM atau Windows DPAPI. Di luar cakupan implementasi saat ini.

## Struktur folder

```
E:\restore_test\encrypt-cli\
  pom.xml
  README.md                    (file ini)
  config\
    app.properties              (kunci AES + pengaturan — jangan commit)
  src\main\java\com\example\encryptapi\
    Main.java                   entry point CLI, subperintah encrypt/decrypt/status
    AppConfig.java               load/generate config & kunci
    FileCrypto.java              enkripsi/dekripsi streaming AES-GCM per file
  target\
    encrypt-cli.jar              hasil build (mvn package)

E:\restore_test\encrypt\          folder yang dipantau (dibuat otomatis kalau belum ada)
  _originals\                     file asli, dipindah ke sini setelah dienkripsi
  <file>.enc                      hasil enkripsi
```
