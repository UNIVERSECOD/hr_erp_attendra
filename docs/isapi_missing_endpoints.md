# ISAPI-də olmayan, UI-də istifadə olunan API endpointləri

Bu sənəd frontend UI hissəsində birbaşa çağırılan, lakin "isapi" modulunda mövcud olmayan API-lərin siyahısını göstərir. Buradakı endpointləri əlavə etmək tövsiyə olunur.

> Qeyd: Siyahı avtomatik çıxarılmayıb, ilkin kontrol üçündür. Tam siyahını çıxarmaq üçün kod baza tam analiz olunmalıdır.

## Addımlar:

1. `isapi` backend controller və service-lərin siyahısı:  
   - `DeviceUserService` (user yaratmaq/oxumaq/yeniləmək/sil)
   - `IsapiAlertStreamRunner` (cihazdan event streamləri)
   - `IsapiProxyService`, s. 
   - Ətraflı bax: [isapi Java mənbə faylları](https://github.com/Saidshi4/hr-erp-back-front/tree/main/isapi/src/main/java)

2. Frontend TypeScript fayllarında birbaşa HTTP çağırışlar (axios/fetch):
   - [ ] `/api/some-custom-endpoint-1`
   - [ ] `/api/some-other-direct-endpoint`
   - [ ] ...

> Tam siyahını çıxarmaq və avtomatlaşdırmaq üçün bütün frontend kodunu nəzərdən keçirin və tək API çağırışlarını qeyd edin.

---

**Əlavə edilməsi tövsiyə olunur:**  
- [ ] Siyahı tam avtomatlaşdırılmış analizlə zənginləşdirilsin (istəsəniz skript köməyi ilə edilə bilər).
- [ ] Buradakı endpointləri isapi moduluna əlavə edəndə, backenddə controller/service yaradaraq tam inteqrasiyanı təmin edin.

---

Bu fayl ilk addım kimi yaradıldı. Gələcəkdə avtomat analiz və tam siyahı üçün kodu toplayıb daha dəqiq doldurun.