# PaperScan — මුද්‍රිත කඩදාසි ඕෆ්ලයින් ස්කෑනරය (Offline Paper Scanner)

**PaperScan** යනු අන්තර්ජාල සබඳතාවයකින් හෝ කිසිදු ඩේටාබේස් එකකින් තොරව, සම්පූර්ණයෙන්ම **ඕෆ්ලයින් (100% Offline)** ලෙස මුද්‍රිත කඩදාසි ස්කෑන් කර දුරකථනයටම සුරැකීමට සහ වෙනත් ඇප් වෙත Share කිරීමට නිර්මාණය කර ඇති Android (Kotlin + Jetpack Compose) යෙදුමකි.

---

## ප්‍රධාන විශේෂාංග (Key Features)

- **100% ඕෆ්ලයින් (No Internet Permission):** කිසිදු අන්තර්ජාල සබඳතාවයක් හෝ Cloud සේවාවක් භාවිත නොකරයි.
- **ඩේටාබේස් නොමැත (Zero Database):** Room, SQLite හෝ Firebase භාවිත නොකර, ස්කෑන් කළ රූප (JPG) සහ බහු-පිටු PDF ගොනු සෘජුවම දුරකථනයේ ගොනු පද්ධතියේ (`filesDir/scanned_docs/` සහ `Downloads/PaperScan/`) සුරැකේ.
- **CameraX + Photo Picker:** කැමරාවෙන් මුද්‍රිත කඩදාසි ස්කෑන් කිරීමට හෝ ගැලරියෙන් ඡායාරූප ආනයනය කිරීමට හැක.
- **කොන් 4 සැකසීම සහ පෙරහන් (Perspective Crop & Filters):**
  - ඇලවී ගත් කඩදාසිවල කොන් 4 ඇද සෘජුකෝණාස්‍රාකාරව සකසා ගැනීම (`Matrix.setPolyToPoly`).
  - **Crisp Color (පැහැදිලි)**, **B&W Print (කළු-සුදු)**, **Grayscale (අළු පැහැ)** සහ **Original (මුල් පිටපත)** පෙරහන්.
- **වෙනත් ඇප් වෙත Share කිරීම:** WhatsApp, Gmail, Telegram, Google Drive ආදී ඕනෑම ඇප් එකකට **PDF** හෝ **JPG** ලෙස Share කළ හැක.
- **ද්විභාෂා සහාය (Sinhala + English):** ප්‍රධාන අතුරුමුහුණත සිංහලෙන් ක්‍රියාත්මක වන අතර ඉහළ ඇති `EN / සිං` බොත්තමෙන් භාෂාව මාරු කළ හැක.

---

## GitHub වෙතින් Clone කර Build කරන ආකාරය (Getting Started)

### අවශ්‍යතා (Prerequisites)
- **JDK 21** (හෝ JDK 17+)
- **Android Studio** (Narwhal / Meerkat හෝ නවතම) — Android SDK 36

### පියවර (Setup Steps)

1. **Repository එක Clone කරන්න:**
   ```bash
   git clone <your-repo-url>
   cd PaperScan
   ```

2. **Debug Keystore එක සකසන්න (පළමු වර Build කිරීමට පෙර):**
   `debug.keystore` ගොනුව GitHub වෙත upload නොවන අතර ඒ වෙනුවට `debug.keystore.base64` ගොනුව ඇතුළත් කර ඇත. Build කිරීමට පෙර පහත විධානය ක්‍රියාත්මක කරන්න:

   - **Linux / macOS:**
     ```bash
     base64 -d debug.keystore.base64 > debug.keystore
     ```
   - **Windows (PowerShell / CMD):**
     ```cmd
     certutil -decode debug.keystore.base64 debug.keystore
     ```

3. **Build සහ Test කරන්න:**
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

4. **Release Build (විකල්ප):**
   Release APK/AAB සෑදීම සඳහා පහත Environment Variables සකසන්න:
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`

---

## GitHub Actions CI
මෙම ව්‍යාපෘතියේ `.github/workflows/android.yml` ඇතුළත් කර ඇති බැවින් GitHub වෙත Push කරන සෑම අවස්ථාවකම ස්වයංක්‍රීයව Unit Tests සහ Debug APK Build පරීක්ෂා කෙරේ.
