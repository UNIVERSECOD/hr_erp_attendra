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

2. Proqramda **Parametrlər → Backup** bölməsinə keçin, Windows qovluğunun tam yolunu yazın və yadda saxlayın.

3. PowerShell-i **Administrator kimi** açıb layihə qovluğunda işlədin:

   ```powershell
   .\scripts\install-backup-task.ps1
   ```

Tapşırıq Windows istifadəçisi daxil olduqdan üç dəqiqə sonra və hər gün saat 04:00-da işə düşür. Docker gec açılarsa skript konteynerlərin hazır olmasını 10 dəqiqəyədək gözləyir. Həmin gün uğurlu backup artıq varsa ikinci surət yaradılmır.

## Əl ilə yoxlama

Planlı tapşırığı gözləmədən sınaq backup-u yaratmaq üçün:

```powershell
.\scripts\backup-attendra.ps1 -Force
```

Backup qovluğunda `.env` ola biləcəyi üçün həmin qovluğa yalnız səlahiyyətli istifadəçilərin girişinə icazə verin. `docker compose down -v` işlətməyin.
