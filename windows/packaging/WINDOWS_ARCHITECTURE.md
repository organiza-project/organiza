# Windows Foreground Tracking & Packaging Architecture

## 1. Native Foreground Tracking Design

Organiza uses unprivileged Win32 APIs to detect active foreground applications without requiring administrator privileges or installing invasive drivers.

### Win32 API Call Sequence
1. **Get Window Handle**: `HWND hwnd = GetForegroundWindow();`
   - Returns the window handle with which the user is currently interacting.
2. **Retrieve Process ID**: `DWORD pid = 0; GetWindowThreadProcessId(hwnd, &pid);`
   - Determines the process ID owning the focused window.
3. **Open Process Handle**:
   - `HANDLE hProcess = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);`
   - Using `PROCESS_QUERY_LIMITED_INFORMATION` allows querying process names of elevated/administrator applications without requiring Organiza itself to run with elevated privileges.
4. **Resolve Executable Path**:
   - `QueryFullProcessImageNameW(hProcess, 0, imagePath, &size);`
   - Yields the canonical executable path (e.g. `C:\Program Files\Google\Chrome\Application\chrome.exe`).
5. **Normalize Identifier**:
   - Strip directory path to extract `chrome.exe` or resolve UWP `PackageFamilyName` via `GetPackageFamilyName`.

### Filter Criteria (Ignored Processes)
The following executables are deliberately excluded from usage tracking:
- `organiza.exe` (Self)
- `explorer.exe` (Desktop shell / taskbar)
- `SearchHost.exe` / `StartMenuExperienceHost.exe`
- `LockApp.exe` (Windows lockscreen)

### Accounting Integration
Detected intervals (5-second increments) are written to local SQLite via `incrementDailyUsage(packageName, appName, durationSeconds)` ensuring exact parity with Linux and Android accounting rules.

---

## 2. Packaging & Distribution

### Inno Setup (Recommended for standard release)
- Configuration: `windows/packaging/organiza.iss`
- Build command:
  ```powershell
  flutter build windows --release
  iscc windows/packaging/organiza.iss
  ```
- Produces signed `Organiza-Setup-1.0.0.exe`.

### MSIX Packaging (Windows Store release)
- Add `msix` package to `pubspec.yaml` or use `MakeAppx.exe`.
- Sign with EV Code Signing certificate or Microsoft Store Ingestion cert.
