# ArcImage (`com.arcimage.app`)

**ArcImage** is a modern, adaptive Material Design 3 (Material You) utility built natively for **Googlebook OS** and **Android 17+ (API 36)** combining an all-in-one **File Archiver / Extractor** and **Batch Image Resizer & Converter**, with full 6-language internationalization and Jetpack Glance desktop/homescreen widgets.

---

## Key Capabilities

### 1. Adaptive Material 3 Architecture (Googlebook OS & Android)
- **Compact Screens (Android Phones):** Branded `TopAppBar`, single-pane navigation (`NavigableListDetailPaneScaffold`), and bottom `NavigationBar`.
- **Expanded Screens (Googlebook OS Laptops & Desktops):** `NavigationRail` with **ArcImage** emblem, live keyboard-shortcut bar, and multi-pane Master-Detail layout optimized for freeform window resizing and high-DPI displays.
- **Desktop Input & Native Drag-and-Drop:**
  - Mouse & haptic trackpad hover highlights and secondary right-click context menus positioned at cursor coordinates.
  - Hardware keyboard shortcuts: `Ctrl+O` (Open), `Ctrl+A` (Select All), `Delete` (Remove Selected), `Esc` (Clear Selection / Close Modal).
  - Cross-window `dragAndDropTarget` with `requestDragAndDropPermissions` for dropping files, recursive folders, archives, and images directly from the Googlebook OS File Manager.
  - Native universal & ABI-split support for `arm64-v8a` and `x86_64` with 16 KB page-size alignment.

### 2. Googlebook OS Continuity, Share Targets & Glance Widget
- **Jetpack Glance Widget (`ArcImageWidget`):** Responsive Material 3 desktop and homescreen widget offering one-tap **"Extract Archive"** and **"Resize/Convert Images"** quick actions.
- **Cross-Device Continuity:** State persistence across freeform window resizes and phone-to-Googlebook handoff via `SavedStateHandle` and `rememberSaveable`.
- **System Share & File Handlers:** Registered for `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, and `ACTION_VIEW` across archive and image MIME types.

### 3. Multi-Language Support (6 Languages + Runtime Switcher)
- Complete localization in **English** (`en`), **German** (`de`), **Italian** (`it`), **French** (`fr`), **Spanish** (`es`), and **Greek** (`el` — full UTF-8 Greek script).
- Instant runtime switching via `AppCompatDelegate.setApplicationLocales` and `res/xml/locales_config.xml`.

### 4. Core Feature 1: File & Folder Archiver / Extractor
- **Compress & Extract:** `.zip` (standard Deflate/Store & AES-256 encrypted via Zip4j), `.7z`, `.tar`, `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.gz`, `.bz2`, `.xz`.
- **Extract & Inspect:** `.rar` and password-protected `.rar` (**RAR4 & RAR5**) via 7-Zip-JBinding-4Android.
- **Security & UX:** Archive Explorer without full extraction, password prompt modals for encrypted ZIP/RAR/7Z headers, Zip-Slip path traversal protection (`ZipSlipGuard`), determinate progress, and cooperative coroutine cancellation.

### 5. Core Feature 2: Batch Image Studio (Resize & Convert)
- **Batch Resizing:** Scale by `25%`, `50%`, `75%`, custom `%`, or exact `Width × Height` pixels with **Maintain Aspect Ratio** lock.
- **Quality & Live Output Estimation:** `1–100%` quality slider with real-time estimated batch output size and Before/After comparison badges.
- **Format Conversion & EXIF Privacy:** Convert across `JPG`, `PNG`, `WEBP`, `GIF` (pure Kotlin GIF89a LZW encoder), `BMP` (32-bit BGRA DIB encoder), and `HEIC/HEIF` (`HeifWriter`), with optional EXIF metadata preservation or stripping (`ExifInterface`).

---

## Building & Testing

```bash
# Run JVM unit tests (codecs, Zip-Slip security, BMP/GIF encoders, ViewModel continuity)
gradle testDebugUnitTest

# Run Android Lint
gradle lintDebug

# Build Universal + arm64-v8a + x86_64 APKs
gradle assembleDebug assembleRelease
```

## Signed Releases

Release builds are signed with the ArcImage **upload key**, stored as GitHub Actions secrets
(`ARCIMAGE_KEYSTORE_BASE64`, `ARCIMAGE_KEYSTORE_PASSWORD`, `ARCIMAGE_KEY_ALIAS`, `ARCIMAGE_KEY_PASSWORD`).
The keystore is never committed (`*.jks` is git-ignored).

```bash
# Publish a release: builds a signed .aab + signed APKs (universal, arm64-v8a, x86_64)
git tag v1.0.0 && git push origin v1.0.0
```

- `versionName` comes from the tag; `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (v1.2.3 → 10203).
- Run **ArcImage Signed Release** manually from the Actions tab to build the files without publishing a release.
- Local signed build: set `ARCIMAGE_KEYSTORE_PATH` and the three password/alias values as environment
  variables or in `~/.gradle/gradle.properties`, then run `gradle bundleRelease` and `gradle assembleRelease` (separately — ABI splits are
  disabled for bundle builds).
  Without them, release builds are produced unsigned.
- For Google Play, enroll in **Play App Signing** and upload the `.aab`; Google holds the app signing key,
  so a lost upload key can be reset.
