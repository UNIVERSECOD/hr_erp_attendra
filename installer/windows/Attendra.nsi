Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "FileFunc.nsh"
Name "Attendra"
OutFile "${OUTPUT}"
InstallDir "$PROGRAMFILES64\Attendra"
InstallDirRegKey HKLM "Software\Attendra" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
!define MUI_ABORTWARNING
!define MUI_ICON "${PAYLOAD}\assets\Attendra.ico"
!define MUI_UNICON "${PAYLOAD}\assets\Attendra.ico"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${PAYLOAD}\assets\header.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${PAYLOAD}\assets\welcome.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${PAYLOAD}\assets\welcome.bmp"
!define MUI_WELCOMEPAGE_TITLE "Attendra quraşdırılması"
!define MUI_WELCOMEPAGE_TEXT "Attendra və tələb olunan xidmətlər bu kompüterə quraşdırılacaq. Docker və ayrıca Java quraşdırılması tələb olunmur.$\r$\n$\r$\nMövcud quraşdırma yenilənirsə, əvvəl məlumatların ehtiyat nüsxəsi alınacaq."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_TITLE "Attendra hazırdır"
!define MUI_FINISHPAGE_TEXT "Proqram Windows xidmətləri kimi işləyir. Masaüstündəki Attendra qısayolu ilə açın.$\r$\n$\r$\nİlk açılışda administrator hesabını yaradın."
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
Function .onInit
 ${IfNot} ${RunningX64}
  MessageBox MB_ICONSTOP "Windows 64-bit tələb olunur."
  Abort
 ${EndIf}
 SetRegView 64
 ReadRegStr $0 HKLM "SYSTEM\CurrentControlSet\Services\AttendraDatabase" "ImagePath"
 ${If} $0 != ""
  StrCpy $1 $0 1
  ${If} $1 == '$\"'
   StrCpy $0 $0 -1 1
  ${EndIf}
  ${GetParent} "$0" $1
  ${GetParent} "$1" $INSTDIR
 ${EndIf}
FunctionEnd
Section "Attendra"
 InitPluginsDir
 SetOutPath "$PLUGINSDIR"
 File "Runtime.ps1"
 File "Manage.ps1"
 ExecWait '"$WINDIR\Sysnative\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$PLUGINSDIR\Manage.ps1" -Action Prepare -InstallRoot "$INSTDIR" -Version "${VERSION}"' $0
 ${If} $0 != 0
  MessageBox MB_ICONSTOP "Quraşdırma yoxlaması keçmədi. Portları və əvvəlki quraşdırmanı yoxlayın. Mövcud məlumatlar saxlanılıb."
  Abort
 ${EndIf}
 SetOutPath "$INSTDIR"
 File /r "${PAYLOAD}\*"
 WriteRegStr HKLM "Software\Attendra" "InstallDir" "$INSTDIR"
 ExecWait '"$INSTDIR\tools\vc_redist.x64.exe" /install /quiet /norestart' $0
 ${If} $0 == 3010
  SetRebootFlag true
 ${ElseIf} $0 != 0
 ${AndIf} $0 != 1638
  MessageBox MB_ICONSTOP "Microsoft Visual C++ runtime quraşdırıla bilmədi."
  SetErrorLevel 1
  Abort
 ${EndIf}
 ExecWait '"$WINDIR\Sysnative\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\scripts\Manage.ps1" -Action Install -InstallRoot "$INSTDIR" -Version "${VERSION}"' $0
 ${If} $0 != 0
  MessageBox MB_ICONSTOP "Xidmətlər tam başlaya bilmədi. C:\ProgramData\Attendra\logs\setup-error.txt faylını yoxlayın. Məlumatlar saxlanılıb; xətanı düzəltdikdən sonra Setup-u yenidən başladın."
  SetErrorLevel 1
  Abort
 ${EndIf}
 WriteUninstaller "$INSTDIR\Uninstall.exe"
 WriteRegStr HKLM "Software\Attendra" "InstallDir" "$INSTDIR"
 WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Attendra" "DisplayName" "Attendra"
 WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Attendra" "DisplayVersion" "${VERSION}"
 WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Attendra" "DisplayIcon" "$INSTDIR\assets\Attendra.ico"
 WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Attendra" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
 SetShellVarContext all
 WriteINIStr "$INSTDIR\Attendra.url" "InternetShortcut" "URL" "http://localhost:3000"
 CreateShortcut "$DESKTOP\Attendra.lnk" "$WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\scripts\Open-Attendra.ps1"' "$INSTDIR\assets\Attendra.ico"
 CreateDirectory "$SMPROGRAMS\Attendra"
 CreateShortcut "$SMPROGRAMS\Attendra\Attendra.lnk" "$WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\scripts\Open-Attendra.ps1"' "$INSTDIR\assets\Attendra.ico"
SectionEnd
Section "Uninstall"
 SetRegView 64
 ExecWait '"$WINDIR\Sysnative\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\scripts\Manage.ps1" -Action Uninstall -InstallRoot "$INSTDIR"' $0
 ${If} $0 != 0
  MessageBox MB_ICONSTOP "Xidmətlər silinə bilmədi. Məlumatlar saxlanılıb."
  Abort
 ${EndIf}
 SetShellVarContext all
 Delete "$DESKTOP\Attendra.lnk"
 Delete "$SMPROGRAMS\Attendra\Attendra.lnk"
 RMDir "$SMPROGRAMS\Attendra"
 Delete "$INSTDIR\Attendra.url"
 Delete "$INSTDIR\Uninstall.exe"
 RMDir "$INSTDIR"
 DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Attendra"
 DeleteRegKey HKLM "Software\Attendra"
 MessageBox MB_OK "Attendra xidmətləri silindi. Baza, fotolar və backup-lar C:\ProgramData\Attendra qovluğunda saxlanıldı."
SectionEnd
