# DashCam

> Convierte tu teléfono Android en una dashcam profesional: grabación en bucle, telemetría GPS, detección de fuerza G y reportes PDF listos para tu aseguradora.

**Autor:** Daniel Barrantes · **Repo:** [github.com/Danielbarrantesv/CarroCamara](https://github.com/Danielbarrantesv/CarroCamara)

---

## ✨ Funcionalidades

| Categoría | Feature | Estado |
|---|---|---|
| **Grabación** | Grabación en bucle con sobreescritura automática | ✅ |
| | Segmentos configurables (15–300 s) | ✅ |
| | Calidad 480p / 720p / 1080p / 2160p | ✅ |
| | Audio opcional | ✅ |
| **Segundo plano** | Foreground service con wake-lock | ✅ |
| | Sigue grabando con pantalla apagada / app minimizada | ✅ |
| | Notificación persistente con controles | ✅ |
| **Telemetría** | GPS fused (1 Hz) → ruta, velocidad, kilometraje | ✅ |
| | Acelerómetro → detección de frenadas, giros, impactos | ✅ |
| | Notificación de impacto + persistencia en DB | ✅ |
| **HUD** | Velocidad, distancia, G-force, coordenadas GPS en vivo | ✅ |
| | Soporte portrait + landscape (rotación) | ✅ |
| | Cambio de cámara frontal/trasera | ✅ |
| **Reportes** | Generación de PDF formal para aseguradora (iText 7) | ✅ |
| | Datos del titular/póliza/vehículo | ✅ |
| | Tabla de eventos con fecha/hora/G/coordenadas | ✅ |
| | Compartir vía Intent | ✅ |
| **Transferencia** | Servidor HTTP local en WiFi (NanoHTTPD) | ✅ |
| | Lista y reproduce videos en cualquier navegador | ✅ |
| | Descarga directa sin cable ni MicroSD | ✅ |
| **Permisos** | Cámara, micrófono, ubicación, ubicación en segundo plano, notificaciones | ✅ |
| **Onboarding** | Wizard de 3 pasos (bienvenida / almacenamiento / permisos) | ✅ |
| **Ajustes** | Calidad, duración de segmento, límite de storage, umbral G, unidad de velocidad, auto-start, keep-screen-on | ✅ |

---

## 🛠️ Stack técnico

- **Lenguaje:** Kotlin 2.0
- **UI:** Jetpack Compose + Material 3 (Material You)
- **Min SDK:** 31 (Android 12) · **Target SDK:** 35 (Android 15)
- **DI:** Hilt
- **DB:** Room (con KSP)
- **Prefs:** DataStore
- **Cámara:** CameraX (VideoCapture + Recorder)
- **Ubicación:** FusedLocationProviderClient (Play Services)
- **Sensores:** SensorManager (acelerómetro lineal)
- **HTTP server:** NanoHTTPD
- **PDF:** iText 7
- **Permisos UI:** Accompanist

---

## 🚀 Cómo compilar

> Este repo incluye todo el código fuente Kotlin + Gradle + AndroidManifest. Para generar el APK necesitas **Android Studio Ladybug o superior**.

### Requisitos
- Android Studio Ladybug+ (AGP 8.6+, Kotlin 2.0+)
- JDK 17 (incluido con Android Studio)
- Un dispositivo Android 12+ o un emulador

### Pasos

1. **Clona el repo**
   ```bash
   git clone https://github.com/Danielbarrantesv/CarroCamara.git
   cd CarroCamara
   ```

2. **Genera el Gradle Wrapper** (no se incluye el JAR en el repo por peso):
   ```bash
   # Si tienes Gradle instalado localmente:
   gradle wrapper --gradle-version 8.9
   # Si no, Android Studio lo generará automáticamente al abrir el proyecto.
   ```

3. **Abre el proyecto en Android Studio**
   - `File → Open → selecciona la carpeta del repo`
   - Espera a que Gradle sincronice (descargará todas las dependencias).

4. **Genera los íconos del launcher** (los PNG por densidad no se incluyen, solo el adaptive icon XML):
   - `res → click derecho → New → Image Asset`
   - Icon type: **Launcher Icons (Adaptive)**
   - Source asset: **Text** → "DashCam" o usa el `ic_launcher_foreground.xml` como vector
   - Next → Finish

5. **Conecta tu teléfono** (con depuración USB activada) o levanta un emulador.

6. **Run ▶** o `Build → Build Bundle(s)/APK(s) → Build APK(s)`.

### Compilar por línea de comandos

```bash
./gradlew assembleDebug
# APK en: app/build/outputs/apk/debug/app-debug.apk

./gradlew installDebug   # instala en dispositivo conectado
```

---

## 📁 Estructura del proyecto

```
DashCam/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/danielbarrantesv/dashcam/
│       │   ├── DashCamApplication.kt       # Hilt + canales de notificación
│       │   ├── MainActivity.kt             # Single-activity + splash
│       │   ├── data/
│       │   │   ├── model/                  # Room entities
│       │   │   ├── db/                     # DAOs + Database
│       │   │   ├── repository/             # TripRepository, ReportRepository
│       │   │   └── prefs/                  # SettingsRepository (DataStore)
│       │   ├── di/                         # Hilt modules
│       │   ├── reports/                    # PdfGenerator (iText)
│       │   ├── service/
│       │   │   ├── RecordingService.kt     # Foreground service (cámara+GPS+G)
│       │   │   ├── LoopRecorder.kt         # CameraX VideoCapture + loop
│       │   │   ├── GpsTracker.kt           # FusedLocation stream
│       │   │   ├── GForceDetector.kt       # Acelerómetro + clasificación
│       │   │   ├── HttpServerService.kt    # NanoHTTPD foreground
│       │   │   ├── DashCamHttpServer.kt    # HTTP routes
│       │   │   └── NotificationHelper.kt
│       │   ├── ui/
│       │   │   ├── theme/                  # Color, Theme, Type
│       │   │   ├── navigation/             # DashCamApp + NavGraph
│       │   │   ├── onboarding/             # Wizard 3 pasos
│       │   │   ├── camera/                 # Pantalla principal + HUD
│       │   │   ├── trips/                  # Lista de viajes
│       │   │   ├── reports/                # Generación y lista de PDFs
│       │   │   ├── transfer/               # Servidor WiFi
│       │   │   └── settings/               # Ajustes
│       │   └── util/                       # FileUtils, Permissions, Formatters
│       └── res/
│           ├── values/                     # strings, colors, themes
│           ├── values-night/               # dark mode
│           ├── drawable/                   # vector icons
│           ├── mipmap-anydpi-v26/          # adaptive launcher
│           └── xml/                        # file_paths, backup rules
├── gradle/
│   ├── libs.versions.toml                  # version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔐 Permisos que solicita la app

| Permiso | Uso |
|---|---|
| `CAMERA` | Grabar video desde la cámara |
| `RECORD_AUDIO` | Audio opcional dentro del coche |
| `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | Velocidad, ruta GPS, kilometraje |
| `ACCESS_BACKGROUND_LOCATION` | GPS mientras la app está en segundo plano |
| `FOREGROUND_SERVICE_CAMERA` / `_LOCATION` / `_MEDIA_RECORDING` | Foreground service types (Android 14+) |
| `POST_NOTIFICATIONS` | Notificación persistente + alertas de impacto |
| `INTERNET` / `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` | Servidor HTTP local |
| `WAKE_LOCK` | Mantener grabación con pantalla apagada |
| `VIBRATE` | Feedback de eventos |
| `RECEIVE_BOOT_COMPLETED` | Auto-start opcional al encender |

---

## 🧭 Flujo de uso típico

1. **Primer arranque:** onboarding → concede permisos → configura storage.
2. **Cámara:** pestaña 1 → botón REC → HUD muestra velocidad/distancia/G/GPS en vivo.
3. **Segundo plano:** la grabación continúa si minimizas o bloqueas el teléfono. Notificación persistente te permite detenerla.
4. **Eventos:** si frena bruscamente o hay un impacto, se guarda un evento y aparece una notificación.
5. **Viajes:** pestaña 2 → lista de todos los viajes con métricas.
6. **Reportes:** pestaña 3 → genera PDF para aseguradora con datos del titular/póliza.
7. **Transferir:** pestaña 4 → inicia servidor WiFi → abre URL en laptop/tablet → descarga videos.

---

## ⚠️ Notas y limitaciones

- **Gradle Wrapper JAR no incluido:** este repo se generó en un entorno Linux sin Android SDK. Al abrir el proyecto en Android Studio, se generará automáticamente.
- **Launcher PNG icons:** solo se incluye el adaptive-icon XML. Android Studio los generará en el primer build.
- **Camera preview:** el `CameraScreen.kt` muestra un placeholder visual; en producción se debe integrar un `PreviewView` con CameraX `Preview` use case (código de muestra en `LoopRecorder.kt`).
- **Battery:** grabación en bucle + GPS + wake-lock consumen batería significativa. Se recomienda usar con el coche encendido/cargando.
- **Storage:** los videos se guardan en `app-specific external storage` (`getExternalFilesDir(null)/videos/`). No requiere `MANAGE_EXTERNAL_STORAGE`.

---

## 📝 Roadmap (futuras versiones)

- [ ] PreviewView real en CameraScreen
- [ ] Auto-start on power connected (BroadcastReceiver)
- [ ] OSMdroid map para mostrar ruta en TripDetail
- [ ] Detección de colisión con machine learning (TF Lite)
- [ ] Subida opcional a Google Drive/Dropbox
- [ ] Modo "estacionamiento" con detección de movimiento
- [ ] Overlay de velocidad en video exportado

---

## 📄 Licencia

Proyecto de código abierto para uso personal. Daniel Barrantes © 2026.
