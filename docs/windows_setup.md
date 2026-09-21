# Windows Mühitində Avtomatik Başlama və Şəbəkə Sazlamaları Təlimatı

Əgər layihənin yerləşdiriləcəyi (deployment) hədəf kompüter **Windows**-dursa, bu sənəddəki addımları yerinə yetirərək sistemin sönüb-yanmasından sonra hər şeyin tam avtomatik və problemsiz işləməsini təmin edə bilərsiniz.

---

## 1. Windows-da Docker Desktop-un Hazırlanması

Docker Desktop-un sistem açılışında avtomatik başlaması üçün aşağıdakı sazlamaları yoxlayın:

1. **Docker Desktop** proqramını açın.
2. Sağ yuxarı küncdəki **Settings** (Çarx işarəsi) bölməsinə daxil olun.
3. **General** menyusunda **"Start Docker Desktop when you log in"** seçimini aktiv edin.
4. Əgər kompüterdə WSL 2 (Windows Subsystem for Linux) quraşdırılıbsa, **"Use the WSL 2 based engine"** seçiminin aktiv olduğunu yoxlayın (bu, performansı ciddi şəkildə artırır).

---

## 2. Windows-un Avtomatik Giriş (Auto-Login) Edilməsi

Windows kompüter sönüb yandıqda, **istifadəçi şifrəsini yazıb ekrana daxil olmayınca (Login ekranında gözləyəndə) Docker Desktop arxa fonda işə düşmür.** Kompüterin server kimi işləməsi və işıq sönüb-yandıqdan sonra heç bir müdaxilə olmadan konteynerlərin işə düşməsi üçün **Auto-Login** qurulmalıdır:

### Sazlanması:
1. Klaviaturada `Win + R` düymələrinə sıxın və açılan pəncərəyə `netplwiz` yazıb **Enter** edin.
2. Açılan pəncərədə öz istifadəçi adınızı (User) seçin.
3. **"Users must enter a user name and password to use this computer"** (Kullanıcılar bu bilgisayarı kullanmak için bir kullanıcı adı ve parola girmelidir) seçimindəki **quşu (check-box) qaldırın**.
4. **Apply (Uygula)** düyməsinə klikləyin.
5. Cari istifadəçi şifrənizi təsdiq etmək üçün tələb olunan pəncərədə şifrənizi daxil edin və yadda saxlayın.

> 💡 **Nəticə:** Artıq kompüter sönüb yandıqda şifrə ekranında gözləmədən birbaşa Masaüstünə (Desktop) daxil olacaq, Docker Desktop avtomatik açılacaq və `restart: always` sayəsində bütün konteynerləriniz (Postgres, Spring Boot backend, ISAPI xidməti, React frontend) öz-özünə aktivləşəcək.

---

## 3. Windows-da `docker-compose` Fayl Yolları (Path) və İcazələr

Nisbi yolların (relative paths) Windows-da Docker tərəfindən düzgün oxunmaması və ya icazə (permission) xətası verməməsi üçün:

1. Layihə qovluğunu Windows-da `C:\hic_project` kimi sadə, qısa və boşluqsuz bir yerdə yerləşdirin.
2. Terminalı (**PowerShell** və ya **CMD**) mütləq **Administrator (Yönetici) olaraq** açın.
3. Layihə qovluğuna daxil olun və sistemi başladın:
   ```powershell
   cd C:\hic_project
   docker compose up -d --build
   ```

---

## 4. Şəbəkə (IP), CORS və Firewall Sazlamaları

Windows kompüteri lokal şəbəkədə (məsələn, ofis daxilində) digər cihazlar və Hikvision cihazları tərəfindən əlçatan olmalıdır.

### Static IP (Sabit IP) Təyini:
* Modemdən və ya Windows şəbəkə sazlamalarından kompüterə sabit lokal IP təyin edin (məsələn: `192.168.0.186`).
* Bu IP-ni layihənin ana qovluğundakı `.env` faylında yeniləyin:
  ```env
  COMPUTER_IP=192.168.1.102
  ```

### CORS Sazlaması:
* Backend container-inin digər cihazlardan sorğu qəbul edə bilməsi üçün `CORS_ALLOWED_ORIGINS` dəyərinə həm `localhost`, həm də kompüterin sabit IP-si əlavə edilib:
  ```yaml
  CORS_ALLOWED_ORIGINS: http://localhost:3000,http://192.168.1.102:3000
  ```

### Windows Firewall (Güvenlik Duvarı) Sazlamaları:
Digər kompüterlərin və fiziki cihazların proqrama qoşula bilməsi üçün müvafiq portlara icazə verilməlidir:
1. Windows Axtarış hissəsinə **"Windows Defender Firewall with Advanced Security"** yazın və açın.
2. Sol menyudan **Inbound Rules (Gelen Kurallar)** bölməsinə daxil olun.
3. Sağ menyudan **New Rule... (Yeni Kural...)** klikləyin.
4. **Port** seçimini edin və növbəti addıma keçin.
5. **TCP** seçin və **Specific local ports** hissəsinə portları daxil edin: `3000, 8080, 8081`
6. **Allow the connection (Bağlantıya izin ver)** seçimini edin.
7. Qaydaya uyğun ad verin (məsələn: `HR ERP System Ports`) və yadda saxlayın.
