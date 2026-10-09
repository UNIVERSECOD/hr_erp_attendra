# Attendra avtomatik backup quraşdırması

Backup bölməsi Windows host-da işləyən planlı tapşırıqla birlikdə istifadə olunur. Skript hər gün ən çox bir uğurlu backup yaradır və 183 gündən köhnə tamamlanmış backup qovluqlarını yalnız yeni backup uğurla bitdikdən sonra silir.

Backup-a aşağıdakılar daxildir:

- `hic_backend` PostgreSQL bazası;
- `hic_isapi` PostgreSQL bazası;
- əməkdaşların üz şəkilləri;
- mövcuddursa `.env` faylının qorunan surəti.

## Bir dəfəlik quraşdırma

1. Layihəni yeniləyin və konteynerləri qurun:

   ```powershell
   git pull --ff-only origin main
   docker compose up -d --build
   ```

2. Proqramda **Parametrlər → Backup** bölməsinə keçin. **Qovluq seç** düyməsi ilə Windows qovluğunu seçin (və ya tam yolu əl ilə yazın) və parametrləri yadda saxlayın.

3. PowerShell-i **Administrator kimi** açıb layihə qovluğunda işlədin:

   ```powershell
   .\scripts\install-backup-task.ps1
   ```

   Skript gündəlik backup tapşırığı ilə yanaşı, yalnız `127.0.0.1:18765` ünvanında işləyən lokal qovluq seçicisini də quraşdırır və başladır. Qovluq seçicisi yalnız `http://localhost:3000` və `http://127.0.0.1:3000` səhifələrindən gələn sorğuları qəbul edir.

Tapşırıq Windows istifadəçisi daxil olduqdan üç dəqiqə sonra və hər gün saat 04:00-da işə düşür. Docker gec açılarsa skript konteynerlərin hazır olmasını 10 dəqiqəyədək gözləyir. Həmin gün uğurlu backup artıq varsa ikinci surət yaradılmır.

Tamamlanmış backup yalnız tarixli qovluq adına görə müəyyən edilmir: `SUCCESS` manifesti, iki PostgreSQL arxivinin `PGDMP` başlığı, `faces` qovluğu və manifestdəki ümumi fayl ölçüsü yoxlanılır. Əvvəlki manifest formatı dəstəklənir. Boş, yarımçıq, strukturu pozulmuş və ya əlavə naməlum faylları olan qovluqlar gündəlik backup-ı dayandırmır və avtomatik silinmir. Junction/simvolik keçid olan qovluqlar da silinmir. Eyni saniyəyə uyğun qovluq artıq varsa yeni boş ad seçilir; mövcud qovluq qorunur.

Bu struktur yoxlaması bazanın uğurla bərpa ediləcəyinə zəmanət vermir; bərpa ayrıca sınaq bazasında yoxlanmalıdır. Windows PowerShell 5.1-də Docker-in müvəqqəti xəta cavabı gözləməni dayandırmır: hər 30 saniyədən bir yenidən yoxlanır, 10 dəqiqə sonra hələ hazır deyilsə xəta statusu yazılır.

Skript Windows PowerShell 5.1 üçün **UTF-8 BOM** ilə saxlanılır; JSON faylları ayrıca UTF-8 kimi oxunur. Beləliklə Azərbaycan hərfli qovluq yolları və status mətnləri qorunur. Backup tarixləri ISO formatında saat qurşağı ilə yazılır. Tamamlanmış backup-dan sonra konteynerdəki müvəqqəti dump fayllarını silmək mümkün olmadıqda status `SUCCESS` qalır, mesajda xəbərdarlıq göstərilir; backup əməliyyatının öz xətası varsa təmizləmə həmin xətanı əvəz etmir.

## Əl ilə yoxlama

Planlı tapşırığı gözləmədən sınaq backup-u yaratmaq üçün:

```powershell
.\scripts\backup-attendra.ps1 -Force
```

Backup qovluğunda `.env` ola biləcəyi üçün həmin qovluğa yalnız səlahiyyətli istifadəçilərin girişinə icazə verin. `docker compose down -v` işlətməyin.

## Avtomatlaşdırılmış skript sınaqları

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/backup-attendra.Tests.ps1
pwsh.exe -NoProfile -File scripts/tests/backup-attendra.Tests.ps1
```

Pester tələb olunmur. Sınaqlar yalnız `build/` altında müvəqqəti məlumatlardan və saxta Docker əmrlərindən istifadə edir; real bazaya və cihazlara qoşulmur. Junction sınaqları üçün həmin müvəqqəti qovluqda junction yaratmağa icazə olmalıdır.

Docker açıq olduqda real backup/bərpa mexanizmini ayrıca yoxlamaq üçün:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/backup-attendra.Docker.Tests.ps1
pwsh.exe -NoProfile -File scripts/tests/backup-attendra.Docker.Tests.ps1
```

Bu sınaq `postgres:15-alpine` əsasında şəbəkəsiz, müvəqqəti konteyner yaradır. İki test bazasının backup-ını alır, ayrıca test bazalarına bərpa edir və məlumatları müqayisə edir; şəkil və `.env` əvəzinə saxta fayllardan istifadə olunur. Sonda yalnız sınağın yaratdığı konteyner və fayllar silinir. Mövcud Attendra konteynerlərinə, volume-lara, müştəri məlumatlarına və fiziki cihazlara müraciət edilmir.
