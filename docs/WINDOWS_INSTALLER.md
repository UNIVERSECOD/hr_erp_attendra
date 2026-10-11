# Attendra Windows Setup

## Müştəri üçün

Paket Windows 10/11 x64 üçün nəzərdə tutulur. Son loqolu sınaq paketi `Attendra-Setup-1.0.1.exe` administrator hüququ ilə açılır. Java, PostgreSQL, Caddy və xidmət wrapper-i paketə daxildir; Docker və internet tələb olunmur. Microsoft Visual C++ x64 runtime da offline quraşdırılır. Setup/uninstall ikonları, quraşdırıcı başlığı və masaüstü/Start qısayollarında mövcud Attendra loqosu istifadə olunur.

- Proqram: `C:\Program Files\Attendra`.
- Baza, konfiqurasiya, fotolar, loglar və standart backup: `C:\ProgramData\Attendra`.
- Açılış: masaüstündəki **Attendra** qısayolu və ya `http://localhost:3000`.
- İlk açılışda administrator hesabı tətbiqin mövcud ilkin quraşdırma ekranından yaradılır. Paketdə hazır müştəri hesabı və ya ortaq şifrə yoxdur.
- `services.msc`: **Attendra Database**, **Attendra Isapi**, **Attendra Backend**, **Attendra Web**. Xidmətlər LocalService hesabı ilə avtomatik/gecikmiş başlayır; dayanma halında 15 saniyədən sonra yenidən başlama sazlanır.
- Xaricə yalnız veb port 3000 açılır (Private/Domain, LocalSubnet). Baza 15432, backend 18080 və ISAPI 18081 yalnız localhost-dadır. Boş port yoxlaması var; mövcud Docker quraşdırması avtomatik dayandırılmır və ya köçürülmür.
- Backup hər gün 04:00-da SYSTEM tapşırığı ilə işləyir; istifadəçinin Windows-a daxil olması tələb olunmur. Qovluq/aktivlik proqramın mövcud Backup parametrlərindən idarə olunur. Qısayol interaktiv qovluq seçicisini də istifadəçi sessiyasında başladır.

## Yeniləmə və silinmə

Eyni qovluğa yeni Setup işlədilir. Əvvəl tətbiq və cihaz xidmətləri dayandırılır, hər iki bazanın və fotoların backup-ı alınır; bu addım uğursuz olsa yeniləmə dayandırılır. Gündəlik backup söndürülmüş olsa da yeniləmə backup-ı məcburidir. Köhnə versiyaya keçid bloklanır. PostgreSQL major versiyası 15 olaraq saxlanılır.

Məlumatlar və şifrələmə açarları yenidən yaradılmır. Uninstall yalnız xidmətləri, proqramın manifestdəki fayllarını, qısayollarını və öz firewall/backup tapşırığını silir. ProgramData içindəki baza, fotolar, açarlar və backup-lar saxlanılır. Başqa qovluğa yenidən quraşdırma mövcud məlumatlar aşkarlanarsa bloklanır.

Native backup-da `.env.backup` adı uyğunluq üçün saxlanılır, məzmun isə `native.json` surətidir. Bu fayl şifrələmə açarları və baza parolu daşıyır; məhdud NTFS hüquqları ilə saxlanılır. Avtomatik bazanı geri qaytarma yoxdur: uğursuz miqrasiyadan sonra backup ayrıca bazada bərpa edilərək yoxlanmalı, köhnə tətbiq və açarlar birlikdə bərpa edilməlidir. İşləyən bazanın üstünə kor-koranə restore edilməməlidir.

## Build

Build kompüterində Node/npm, Maven, Java 21 JDK və PowerShell lazımdır. Müştəriyə bunların ayrıca quraşdırılması tələb olunmur.

```powershell
.\installer\windows\Get-Dependencies.ps1
.\installer\windows\Build-Setup.ps1 -JavaHome 'C:\Tools\jdk-21' -NsisDirectory "$PWD\build\windows-downloads\nsis\nsis-3.13" -DependenciesDirectory "$PWD\build\windows-downloads" -Version 1.0.0
```

`-UseBuiltArtifacts` yalnız əvvəl yoxlanmış `backend/target`, `isapi/target`, `frontend/dist` artefaktları üçün istifadə olunur. Nəticə `build/windows-installer` altına `.exe` və `.sha256` kimi yazılır. `dependencies.json` rəsmi buraxılış URL-lərini və gözlənilən SHA256-ları sabitləyir. Upstream mutable Microsoft URL-i dəyişərsə hash yoxlaması build-i dayandırır; yeni buraxılış ayrıca yoxlanıb pin yenilənməlidir. Payload `files.json` ilə quraşdırmada yenidən yoxlanır. Bu manifest rəqəmsal imzanı əvəz etmir.

## Yoxlama və hazırkı məhdudiyyət

`Test-Native.ps1 -Payload <build-payload-yolu>` real cihazlara qoşulmadan yeni native PostgreSQL bazasında Java xidmətlərini və Caddy-ni 39100–39103 portlarında yoxlayır. Baza miqrasiyaları, SPA/proxy, idempotent baza hazırlığı, backup, hər iki bazanın ayrıca bərpası, foto və açar saxlanması sınaqdan keçirilir. Bu sınaq xidmət qeydiyyatı etmir və mövcud Docker verilənlərinə toxunmur.

İlk paket sınaq buraxılışıdır. Bu sessiyada administrator tokeni olmadığından aşağıdakılar təmiz Windows VM-də hələ yoxlanmalıdır:

1. Offline Setup ilə SCM xidmət qeydiyyatı və LocalService hüquqları.
2. Windows hesabına daxil olmadan reboot sonrası avtomatik açılış.
3. Setup-un ikinci dəfə çalışdırılması, daha yeni versiyaya keçid və uninstall zamanı məlumatların qalması.
4. Yalnız OS və paketdəki VC++ runtime olan kompüterdə açılış.

Paket hələ code-signing sertifikatı ilə imzalanmayıb. Cari Docker məlumatlarının native quraşdırmaya avtomatik köçürülməsi bu paketə daxil deyil. Mövcud real cihazlarla yoxlama aparılmayıb. LAN üzərindən kamera üçün brauzerin HTTPS tələbi ayrıca sertifikat/deployment sazlaması tələb edir.
