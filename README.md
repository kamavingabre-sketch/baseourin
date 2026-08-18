# Hallo Johor — Ourin Baileys

Bot WhatsApp dan dashboard layanan Kecamatan Medan Johor. Aplikasi ini dimigrasikan dari snapshot
[`HALLOJOHORFIX@60bfb49`](https://github.com/kamavingabre-sketch/HALLOJOHORFIX/tree/60bfb497784fd149c286810e17ca63629826e885)
agar memakai **Ourin Baileys** (`ourin-baileys`) sebagai pengganti Baileys standar.

## Perubahan basis WhatsApp

- Dependency resmi `@whiskeysockets/baileys` diganti menjadi alias npm:
  `"ourin": "npm:ourin-baileys@^9.0.11"`.
- Semua import API WhatsApp menggunakan module `ourin`.
- Versi koneksi diambil melalui `fetchLatestWaWebVersion()` dari Ourin Baileys.
- Folder auth tetap `auth_info_baileys`, sehingga sesi lama tetap berada di lokasi yang sama.

Fitur layanan, dashboard, Supabase, pairing code, dan struktur data Hallo Johor dipertahankan.

## Sistem Multi-Akun (Superadmin + 6 Admin Kelurahan)

Dashboard mendukung dua level akun yang datanya tersimpan di tabel `admin_users` (Supabase):

| Role | Hak akses |
| --- | --- |
| 👑 **Superadmin** | Akses penuh: semua laporan 6 kelurahan, kelola akun, log aktivitas, grup WA, routing, kegiatan, UMKM, broadcast, export Excel, IVA, hapus laporan, dll. |
| 🏘️ **Admin Kelurahan** | Hanya laporan wilayahnya: membaca laporan, **menanggapi laporan** (balasan WA ke pelapor), **mengubah status laporan**, dan **membalas live chat**. Fitur lain diblokir di server. |

Cara pakai:

1. Jalankan ulang [`supabase_schema.sql`](./supabase_schema.sql) di Supabase SQL Editor (aman — memakai `IF NOT EXISTS`) untuk membuat tabel `admin_users` dan `admin_activity_log`.
2. Saat pertama kali berjalan dan tabel akun masih kosong, aplikasi otomatis membuat **akun superadmin** dari `ADMIN_USER`/`ADMIN_PASS`.
3. Login sebagai superadmin → buka menu **👥 Akun Admin** → buat 6 akun admin kelurahan (contoh: `admin.gedungjohor`, `admin.pangkalanmasyhur`, `admin.kwalabekala`, `admin.kedaidurian`, `admin.sukamaju`, `admin.titikuning`) lalu bagikan username + password ke petugas masing-masing kelurahan.
4. Buka menu **📜 Log Aktivitas** untuk memantau seluruh aktivitas admin: login, ubah status, balasan laporan, balasan/penutupan live chat, hingga kinerja per admin (jumlah tanggapan, chat dibalas, waktu aktivitas terakhir).

Semua aksi admin — termasuk superadmin — tercatat di tabel `admin_activity_log` beserta username, kelurahan, detail aksi, IP, dan waktu.

## Persyaratan

- Node.js 20 atau lebih baru
- Project Supabase yang sudah menjalankan [`supabase_schema.sql`](./supabase_schema.sql)
- Nomor WhatsApp untuk pairing

## Menjalankan secara lokal

```bash
cp .env.example .env
# Isi seluruh variabel wajib di .env, lalu ekspor ke shell Anda.
npm install
npm start
```

Node.js tidak membaca `.env` secara otomatis. Anda dapat mengekspornya dengan alat pilihan Anda, atau menjalankan:

```bash
set -a; source .env; set +a; npm start
```

`npm start` menjalankan bot WhatsApp (`index.js`) dan dashboard (`web.js`) secara bersamaan.
Dashboard tersedia pada `http://localhost:3000` secara default.

## Aplikasi Android Kotlin

Folder [`android/`](./android) berisi aplikasi Android Kotlin + Jetpack Compose yang memakai layanan yang sama dengan bot:

- Informasi persyaratan surat, PBB, kontak, Pintar Johor, program, wisata, kegiatan, dan UMKM.
- Pengaduan masyarakat dengan kategori, kelurahan, lokasi, dan foto opsional.
- Status laporan yang dikirim dari aplikasi.
- LiveChat dengan admin Dashboard.
- Skrining IVA Test.

Aplikasi **tidak** berisi Supabase service-role key. Aplikasi hanya memanggil endpoint `/api/mobile/*` di server Node. Event penggunaan fitur disimpan pada tabel `feature_usage` dan ditampilkan di kartu **Aktivitas Aplikasi Android** pada Dashboard Admin. Laporan aplikasi juga masuk ke `laporan_archive`, sehingga admin dapat mengelola statusnya seperti laporan WhatsApp.

### Build melalui GitHub Actions

Workflow [`android.yml`](./.github/workflows/android.yml) otomatis menghasilkan `app-debug.apk` dan `app-release-unsigned.apk` sebagai artifact. Di GitHub repository, buat:

- **Actions secret** `API_BASE_URL` = URL publik Railway yang menjalankan Dashboard, contoh `https://nama-service.up.railway.app`.
- Jalankan workflow **Build Android Kotlin** dari tab **Actions**, atau push perubahan pada folder `android/`.

Untuk build manual pada runner yang memiliki Gradle 8.7 dan Java 17:

```bash
cd android
gradle :app:assembleDebug -PAPI_BASE_URL=https://nama-service.up.railway.app
```

APK release dari workflow belum ditandatangani. Untuk distribusi Play Store, tambahkan signing ke workflow dan simpan keystore sebagai GitHub Actions secrets.

### Menyiapkan backend mobile

Setelah menambahkan versi ini, jalankan ulang seluruh [`supabase_schema.sql`](./supabase_schema.sql) di Supabase SQL Editor agar tabel `feature_usage` dan `mobile_report_queue` dibuat. Deploy Node/Railway seperti biasa. Worker bot akan meneruskan laporan dari aplikasi ke grup WhatsApp yang terdaftar; arsip Dashboard tetap dibuat walaupun bot sedang offline.

## Environment variables

| Variable | Wajib | Keterangan |
| --- | --- | --- |
| `SUPABASE_URL` | Ya | URL project Supabase |
| `SUPABASE_SERVICE_KEY` | Ya | Service-role key Supabase; jangan gunakan anon key |
| `PHONE_NUMBER` | Saat pairing | Nomor WA format `628...`, tanpa tanda `+` |
| `ADMIN_USER` | Disarankan | Username dashboard; default aplikasi adalah `admin` |
| `ADMIN_PASS` | Disarankan | Password dashboard; wajib diganti untuk produksi |
| `PORT` | Tidak | Port dashboard; Railway mengisinya otomatis |
| `AUTH_CREDS` | Tidak | Kredensial auth base64 untuk deployment tanpa volume |

## Deployment

- Panduan Railway: [`RAILWAY_DEPLOY.md`](./RAILWAY_DEPLOY.md)
- Panduan database: [`SUPABASE_SETUP.md`](./SUPABASE_SETUP.md)

> Jangan commit `.env`, service-role key, atau isi folder `auth_info_baileys`.
