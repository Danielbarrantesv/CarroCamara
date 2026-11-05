# Guía para trabajar el proyecto DashCam

> **Para: IA que va a continuar el proyecto**
> **De: IA que lo desarrolló hasta v1.1.0**
> **Proyecto:** DashCam — App Android de dashcam con grabación en bucle, GPS, G-force, reportes PDF y 6 idiomas
> **Repo:** https://github.com/Danielbarrantesv/CarroCamara
> **Última versión:** v1.1.0-debug (con grabación en background)

---

## 🧠 CONTEXTO CRÍTICO — Lee esto PRIMERO

Estás trabajando en un proyecto Android (Kotlin + Jetpack Compose) en un sandbox Linux que **se recicla entre sesiones**. Esto significa:

1. **Todo bajo `/home/z/my-project/` se conserva** entre sesiones (código, git, APKs en `download/`)
2. **Las tools (JDK, Gradle, Android SDK) se BORRAN** cuando el sandbox se recicla — hay que reinstalarlas
3. **El cache de Gradle (`~/.gradle/`) también se borra** — hay que descargar dependencias de nuevo
4. **Maven Central tiene rate-limiting intermitente (HTTP 429)** — a veces los builds fallan por esto, no por errores de código. Hay un loop de reintentos que lo soluciona.
5. **Antes de cerrar la sesión, SIEMPRE haz commit y push** — si no, pierdes los cambios

---

## 🛠️ SETUP INICIAL (ejecutar al inicio de cada sesión)

### Paso 1: Verificar si las tools ya existen

```bash
ls /home/z/tools/env.sh 2>/dev/null && echo "Tools OK" || echo "NEED REINSTALL"
```

### Paso 2: Si NO existen, reinstalar todo

Copia literal este bloque completo:

```bash
mkdir -p /home/z/tools && cd /home/z/tools

# Descargar JDK 17, Gradle 8.9 y Android cmdline-tools en paralelo
( wget -q "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz" -O jdk17.tar.gz && tar xzf jdk17.tar.gz && rm jdk17.tar.gz && echo "JDK OK" ) &

( wget -q "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -O gradle.zip && unzip -qo gradle.zip && rm gradle.zip && echo "Gradle OK" ) &

( mkdir -p /home/z/tools/android-sdk/cmdline-tools && cd /tmp && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O cdt.zip && unzip -qo cdt.zip -d /home/z/tools/android-sdk/cmdline-tools && mv /home/z/tools/android-sdk/cmdline-tools/cmdline-tools /home/z/tools/android-sdk/cmdline-tools/latest && rm cdt.zip && echo "SDK tools OK" ) &

wait
echo "=== Downloads completados ==="
```

### Paso 3: Configurar Android SDK (licencias + platform-tools + build-tools)

```bash
export JAVA_HOME=/home/z/tools/jdk-17.0.13+11
export ANDROID_HOME=/home/z/tools/android-sdk
export ANDROID_SDK_ROOT=/home/z/tools/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null 2>&1
echo "=== SDK configurado ==="
```

### Paso 4: Crear archivo env.sh para sourcear antes de cada build

```bash
cat > /home/z/tools/env.sh << 'EOF'
export JAVA_HOME=/home/z/tools/jdk-17.0.13+11
export ANDROID_HOME=/home/z/tools/android-sdk
export ANDROID_SDK_ROOT=/home/z/tools/android-sdk
export GRADLE_HOME=/home/z/tools/gradle-8.9
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$GRADLE_HOME/bin:$PATH
EOF
echo "=== env.sh creado ==="
```

### Paso 5: Crear local.properties (ruta del SDK para Gradle)

```bash
cd /home/z/my-project
echo "sdk.dir=/home/z/tools/android-sdk" > local.properties
```

---

## 🔨 BUILD — Cómo compilar el APK

### Build simple

```bash
source /home/z/tools/env.sh
cd /home/z/my-project
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
./gradlew :app:assembleDebug --no-daemon
```

### Si Maven Central da HTTP 429 (Too Many Requests) — Loop de reintentos

**Este es el problema más común.** Maven Central rate-limita el User-Agent de Gradle. La solución es un loop que descarga los artifacts fallidos manualmente con curl (que usa User-Agent de navegador):

```bash
source /home/z/tools/env.sh
cd /home/z/my-project
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

for i in $(seq 1 10); do
  echo "=== Intento $i/10 ==="
  OUTPUT=$(./gradlew :app:assembleDebug --no-daemon 2>&1)
  echo "$OUTPUT" | tail -5
  
  if echo "$OUTPUT" | grep -q "BUILD SUCCESSFUL"; then
    echo "=== BUILD SUCCESSFUL en intento $i ==="
    break
  fi
  
  # Descargar artifacts fallidos manualmente con browser UA
  echo "=== Descargando artifacts fallidos ==="
  echo "$OUTPUT" | grep -oP "https://repo\.maven\.apache\.org/[^ ']+" | sort -u | while read url; do
    tmpfile=$(mktemp)
    curl -sS -L -H "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36" -o "$tmpfile" "$url" 2>/dev/null
    size=$(stat -c%s "$tmpfile" 2>/dev/null || echo 0)
    if [ $size -gt 0 ]; then
      sha1=$(sha1sum "$tmpfile" | cut -d' ' -f1)
      path=$(echo "$url" | sed 's|https://repo.maven.apache.org/maven2/||')
      filename=$(basename "$url")
      version=$(basename $(dirname "$url"))
      artifact=$(basename $(dirname $(dirname "$url")))
      groupdir=$(dirname $(dirname $(dirname "$url")) | tr '/' '.')
      cachedir="$HOME/.gradle/caches/modules-2/files-2.1/$groupdir/$artifact/$version/$sha1"
      mkdir -p "$cachedir"
      cp "$tmpfile" "$cachedir/$filename"
      echo "  OK: $filename ($size bytes)"
    fi
    rm -f "$tmpfile"
  done
  
  find /home/z/.gradle/caches -name "*.lastDownload" -delete 2>/dev/null
  echo "=== Esperando 30s antes de reintentar ==="
  sleep 30
done
```

### Ubicación del APK generado

```
/home/z/my-project/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📤 SUBIR APK A GITHUB RELEASES

### Crear release

```bash
cd /home/z/my-project

RESPONSE=$(curl -sS --max-time 30 -X POST \
  -H "Authorization: token GITHUB_TOKEN_AQUI" \
  -H "Accept: application/vnd.github+json" \
  -d '{
    "tag_name": "vX.Y.Z-debug",
    "name": "DashCam vX.Y.Z (descripción corta)",
    "body": "Descripción de los cambios",
    "draft": false,
    "prerelease": true,
    "target_commitish": "main"
  }' \
  https://api.github.com/repos/Danielbarrantesv/CarroCamara/releases)

REL_ID=$(echo "$RESPONSE" | grep -oE '"id":[[:space:]]*[0-9]+' | head -1 | grep -oE '[0-9]+')
echo "Release ID: $REL_ID"
```

### Subir APK al release

```bash
curl -sS --max-time 280 -X POST \
  -H "Authorization: token GITHUB_TOKEN_AQUI" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @app/build/outputs/apk/debug/app-debug.apk \
  "https://uploads.github.com/repos/Danielbarrantesv/CarroCamara/releases/${REL_ID}/assets?name=DashCam-debug-vX.Y.Z.apk&label=DashCam-debug-vX.Y.Z.apk"
```

---

## 📦 COMMIT Y PUSH A GITHUB

**ANTES DE CERRAR CADA SESIÓN, SIEMPRE haz commit y push:**

```bash
cd /home/z/my-project

# Ver qué cambió
git status
git diff --stat

# Commit
git add -A
git commit -m "fix: vX.Y.Z - descripción de cambios

Detalles técnicos del cambio..."

# Push (usa el token inline, NO lo guardes en ningún archivo)
git push https://Danielbarrantesv:GITHUB_TOKEN_AQUI@github.com/Danielbarrantesv/CarroCamara.git main
```

### Si el push falla por archivos grandes

```bash
# Verificar archivos grandes
find . -size +50M -not -path "./.git/*" -not -path "./app/build/*" -not -path "./download/*"

# Eliminar gradle.zip o gradle-8.9/ si existen (NO deben commitearse)
rm -f gradle.zip
rm -rf gradle-8.9/

# Asegurar que .gitignore incluye estas entradas
# tools/
# gradle-8.9/
# local.properties
# tool-results/
# upload/
```

---

## 📁 ESTRUCTURA DEL PROYECTO

```
/home/z/my-project/
├── app/
│   ├── build.gradle.kts              # Config del módulo app
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml       # Permisos, services, FileProvider
│       ├── java/com/danielbarrantesv/dashcam/
│       │   ├── DashCamApplication.kt # Hilt + canales notificación + locale startup
│       │   ├── MainActivity.kt       # AppCompatActivity (importante para locale)
│       │   ├── data/
│       │   │   ├── model/            # Room entities (Trip, VideoSegment, GForceEvent, etc.)
│       │   │   ├── db/               # DAOs + DashCamDatabase
│       │   │   ├── repository/       # TripRepository, ReportRepository
│       │   │   └── prefs/            # SettingsRepository (DataStore) + enums
│       │   ├── di/                   # Hilt modules (DatabaseModule)
│       │   ├── reports/              # PdfGenerator (iText 7)
│       │   ├── service/
│       │   │   ├── RecordingService.kt    # Foreground service + wake lock
│       │   │   ├── NotificationHelper.kt
│       │   │   ├── GpsTracker.kt
│       │   │   ├── GForceDetector.kt
│       │   │   ├── HttpServerService.kt   # WiFi transfer
│       │   │   ├── DashCamHttpServer.kt   # NanoHTTPD
│       │   │   └── LoopRecorder.kt        # (legacy, no se usa activamente)
│       │   ├── ui/
│       │   │   ├── theme/            # Color.kt, Theme.kt, Type.kt
│       │   │   ├── navigation/       # DashCamApp.kt (NavHost + bottom bar)
│       │   │   ├── onboarding/       # OnboardingFlow.kt (5 pasos)
│       │   │   ├── camera/           # CameraScreen.kt + CameraViewModel.kt
│       │   │   ├── trips/            # TripsScreen + TripsViewModel + VideoPlayer
│       │   │   ├── reports/          # ReportsScreen + ReportsViewModel
│       │   │   ├── transfer/         # TransferScreen + TransferViewModel
│       │   │   ├── settings/         # SettingsScreen + SettingsViewModel
│       │   │   └── MainViewModel.kt
│       │   └── util/                 # FileUtils, Formatters, Permissions
│       └── res/
│           ├── values/               # strings.xml (español default)
│           ├── values-en/            # English
│           ├── values-fr/            # Français
│           ├── values-zh/            # 中文
│           ├── values-ja/            # 日本語
│           ├── values-de/            # Deutsch
│           ├── values-night/         # dark mode
│           ├── drawable/             # vector icons
│           ├── mipmap-anydpi-v26/    # adaptive launcher
│           └── xml/                  # file_paths, backup_rules, locales_config
├── gradle/
│   ├── libs.versions.toml            # version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts                  # Config root
├── settings.gradle.kts               # Repositorios Maven
├── gradle.properties                 # JVM args + AndroidX + encoding
├── gradlew                           # Wrapper Unix
├── gradlew.bat                       # Wrapper Windows
├── .gitignore
├── local.properties                  # sdk.dir (NO commitear)
└── README.md
```

---

## 🔑 STACK TÉCNICO

| Componente | Versión | Notas |
|---|---|---|
| **Kotlin** | 2.0.21 | |
| **Jetpack Compose** | BOM 2024.10.01 | |
| **Material 3** | (via BOM) | Theme.AppCompat.DayNight.NoActionBar |
| **Hilt** | 2.52 | DI |
| **Room** | 2.6.1 | con KSP |
| **CameraX** | 1.4.0 | VideoCapture + Recorder + ConcurrentCamera |
| **Media3 ExoPlayer** | 1.4.1 | Reproducción de video |
| **iText 7** | 7.2.5 | Generación de PDF |
| **NanoHTTPD** | 2.3.1 | Servidor HTTP WiFi |
| **Accompanist** | 0.36.0 | Permisos |
| **DataStore** | 1.1.1 | Preferences |
| **Play Services Location** | 21.3.0 | GPS |
| **Min SDK** | 31 | Android 12 |
| **Target SDK** | 35 | Android 15 |
| **JDK** | 17 (Temurin) | NO usar 21 |
| **Gradle** | 8.9 | |
| **AGP** | 8.6.1 | Android Gradle Plugin |

---

## ⚠️ PROBLEMAS CONOCIDOS Y SOLUCIONES

### 1. `MalformedInputException` (charset)
**Síntoma:** Gradle falla con `java.nio.charset.MalformedInputException: Input length = 1`
**Causa:** El strings.xml tiene caracteres UTF-8 pero Gradle usa charset del sistema
**Solución:**
```bash
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
```
Y en `gradle.properties`:
```properties
org.gradle.encoding=utf-8
```

### 2. Maven Central HTTP 429 (Too Many Requests)
**Síntoma:** `Could not download X.jar ... Received status code 429`
**Causa:** Maven Central rate-limita el User-Agent de Gradle
**Solución:** Usar el loop de reintentos con descarga manual (ver sección BUILD)

### 3. `perm_camera_title` duplicado
**Síntoma:** `Error: Found item String/perm_camera_title more than one time`
**Causa:** Los strings de permisos existen al inicio del strings.xml original Y se duplicaron después
**Solución:** Eliminar los duplicados — mantener solo una copia de cada string key

### 4. Camera crash `No supported surface combination`
**Síntoma:** `CameraUseCaseAdapter$CameraException: No supported surface combination is found for camera device - Id: 1`
**Causa:** La cámara frontal no soporta Preview + VideoCapture a FHD simultáneamente
**Solución:**
- `FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)` en `buildRecorder()`
- **NO** usar `setTargetAspectRatio()` en el Preview
- Try/catch progresivo: Preview+VideoCapture → solo VideoCapture → HD fallback

### 5. Idioma no cambia
**Síntoma:** Se selecciona otro idioma pero la UI sigue en español
**Causa:** 
- `MainActivity` debe extender `AppCompatActivity` (no `ComponentActivity`)
- Los strings deben usar `stringResource(R.string.*)` — no hardcoded
- Tras seleccionar idioma: `AppCompatDelegate.setApplicationLocales()` + `activity.recreate()`
**Solución:** Ya implementado en v1.0.6+. Si vuelve a fallar, verificar que `MainActivity` extiende `AppCompatActivity`.

### 6. Video se pierde al cambiar de pestaña o pantalla apagada
**Síntoma:** Se graba, se cambia de pestaña, el video no se guarda o está vacío
**Causa:** La cámara se bind al lifecycle del NavBackStackEntry, que se detiene al cambiar de pestaña
**Solución:** Ya implementado en v1.1.0:
- `AlwaysResumedLifecycleOwner` — la cámara se bind a un lifecycle siempre-RESUMED
- `RecordingService` foreground service + `PARTIAL_WAKE_LOCK`
- Si vuelve a fallar, verificar que `RecordingService.start(ctx)` se llama en `startRecording()`

### 7. Gradle daemon desaparece
**Síntoma:** `Gradle build daemon disappeared unexpectedly`
**Causa:** RAM limitada del sandbox (4GB)
**Solución:** Usar `--no-daemon` SIEMPRE. Y en `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Dhttp.agent=Mozilla/5.0
```

### 8. APOSTROFES en strings.xml (francés)
**Síntoma:** `Invalid unicode escape sequence in string` o `does not contain a valid string resource`
**Causa:** Apóstrofes sin escapar en francés (l'enregistrement)
**Solución:** Escapar con `\'`:
```xml
<string name="camera_dual_not_supported">...l\'enregistrement...</string>
```

### 9. Push rechazado por archivos grandes
**Síntoma:** `remote: error: File gradle.zip is 129.81 MB; this exceeds GitHub's file size limit`
**Causa:** Se descargó gradle.zip o gradle-8.9/ dentro del repo
**Solución:**
```bash
rm -f gradle.zip
rm -rf gradle-8.9/
# Asegurar que .gitignore incluye:
# tools/
# gradle-8.9/
# local.properties
# tool-results/
# upload/
git rm -r --cached gradle-8.9 2>/dev/null
git add -A
git commit --amend --no-edit
git push --force https://TOKEN@github.com/Danielbarrantesv/CarroCamara.git main
```

---

## 🚨 REGLAS CRÍTICAS PARA NO PERDER CAMBIOS

1. **NUNCA hagas `git reset --hard` o `git checkout .` sin confirmar primero** — puedes perder cambios sin commitear

2. **Antes de cualquier operación destructiva, haz commit:**
   ```bash
   git add -A && git commit -m "WIP: antes de operación destructiva"
   ```

3. **El archivo `local.properties` es local — NO lo commitees** (ya está en .gitignore)

4. **Las tools van en `/home/z/tools/` — NO las pongas dentro del repo** (ya está en .gitignore)

5. **Para ver el estado actual del proyecto:**
   ```bash
   git log --oneline -5          # últimos commits
   git status                    # cambios sin commitear
   git diff --stat               # resumen de cambios
   ```

6. **ANTES DE CERRAR LA SESIÓN, SIEMPRE:**
   ```bash
   cd /home/z/my-project
   git add -A
   git commit -m "WIP: cambios antes de cerrar sesión"
   git push https://TOKEN@github.com/Danielbarrantesv/CarroCamara.git main
   ```

7. **Si pierdes cambios, recupera de GitHub:**
   ```bash
   cd /home/z/my-project
   git fetch origin
   git reset --hard origin/main  # ⚠️ SOLO si no hay cambios locales sin commitear
   ```

---

## 📋 CHECKLIST DE CADA SESIÓN

### Al INICIAR la sesión:
- [ ] Verificar que `/home/z/my-project/` existe (código del proyecto)
- [ ] Verificar si las tools existen: `ls /home/z/tools/env.sh`
- [ ] Si NO existen → ejecutar setup completo (Paso 2-5)
- [ ] Crear `local.properties` con `sdk.dir=/home/z/tools/android-sdk`
- [ ] Verificar estado del git: `git log --oneline -3 && git status`

### Al HACER CAMBIOS:
- [ ] Hacer commit frecuentemente: `git add -A && git commit -m "WIP"`
- [ ] Si migras strings a stringResource, añadirlos a los 6 locales (es/en/fr/zh/ja/de)
- [ ] Si cambias el manifest, verificar que los foregroundServiceType son válidos

### Al COMPILAR:
- [ ] `source /home/z/tools/env.sh`
- [ ] `export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"`
- [ ] `./gradlew :app:assembleDebug --no-daemon`
- [ ] Si falla por 429 → usar el loop de reintentos
- [ ] Verificar que el APK existe: `ls -lh app/build/outputs/apk/debug/app-debug.apk`

### Al SUBIR APK:
- [ ] Copiar APK a `download/` con nombre descriptivo
- [ ] Crear release en GitHub con tag `vX.Y.Z-debug`
- [ ] Subir APK como asset del release
- [ ] Commit y push del código

### Al CERRAR la sesión:
- [ ] **SIEMPRE** commit + push (aunque sea WIP)
- [ ] Verificar que el push fue exitoso
- [ ] Anotar la versión actual para la próxima sesión

---

## 🔍 CÓMO VERIFICAR LA VERSIÓN ACTUAL

```bash
# Ver el último release en GitHub
curl -sS -H "Authorization: token TOKEN" \
  https://api.github.com/repos/Danielbarrantesv/CarroCamara/releases \
  | python3 -c "import json,sys; [print(f'{r[\"tag_name\"]}: {r[\"name\"]}') for r in json.load(sys.stdin)[:5]]"

# Ver los últimos commits
cd /home/z/my-project
git log --oneline -10

# Ver la versión en el código
grep "settings_about_version" app/src/main/res/values/strings.xml
```

---

## 📝 HISTORIAL DE VERSIONES

| Versión | Cambios principales |
|---|---|
| v1.0.0 | Initial commit — proyecto base |
| v1.0.1 | Camera preview real + REC button fix |
| v1.0.2 | Video playback + configurable storage + dual camera toggle |
| v1.0.3 | Custom storage folder + filenames with date + 6 languages |
| v1.0.4 | Language switching + dual camera graceful + persistent recording |
| v1.0.5 | QA-driven fixes (camera fallback, permissions UI, activity-scoped VM) |
| v1.0.6 | AppCompatActivity + stringResource migration (language fix) |
| v1.0.7 | ContextWrapper approach (revertido — no funcionó) |
| v1.0.8 | Vuelve a v1.0.6 + 4 fixes puntuales (StorageStep, PermissionsStep, Acerca de, refresco permisos) |
| v1.0.9 | Camera aspect ratio fix + bottom padding + language selection persistence + Continuar i18n + dual cam msg i18n |
| **v1.1.0** | **Background recording (foreground service + wake lock + AlwaysResumedLifecycleOwner) + partial file saving** |

---

## 🆘 SI NADA FUNCIONA — Recuperación completa

```bash
# 1. Reinstalar todo desde cero
rm -rf /home/z/tools /home/z/.gradle
# (ejecutar setup completo del Paso 2-5)

# 2. Recuperar código de GitHub
cd /home/z/my-project
git fetch origin
git reset --hard origin/main

# 3. Build
source /home/z/tools/env.sh
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
./gradlew :app:assembleDebug --no-daemon
# (usar loop de reintentos si hay 429)
```

---

## 📞 CONTACTO

- **Autor:** Daniel Barrantes Villarevia
- **Repo:** https://github.com/Danielbarrantesv/CarroCamara
- **Última versión:** https://github.com/Danielbarrantesv/CarroCamara/releases/latest

---

**Recuerda:** El sandbox se recicla. **ANTES DE CERRAR, SIEMPRE COMMIT + PUSH.**
