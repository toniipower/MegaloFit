# MegaloFit

App de fitness para Android, minimalista y en cuadrícula, para entrenar **tus** rutinas.
Entrena a lo grande. 💪

## Qué hace ya (v0.1)

- **Catálogo** de ~60 ejercicios en español (`app/src/main/assets/exercises.json`) con búsqueda
  (sin importar tildes) y filtro por grupo muscular. Puedes añadir tus propios ejercicios.
- **Rutinas**: crea, renombra y borra rutinas. Añade ejercicios desde el catálogo (selección
  múltiple, en el orden en que los tocas) y configura por ejercicio: series, repeticiones **o**
  tiempo de trabajo, y descanso. Reordenar con "Antes / Después". Duración estimada.
- **Temporizador fiable**: Foreground Service + StateFlow. No cuenta ticks: guarda el instante
  en que acaba cada fase (`elapsedRealtime`), así que no se desfasa aunque bloquees el móvil,
  cambies de app o la CPU duerma. Notificación con cuenta atrás y botones Pausa / Saltar /
  Terminar, vibración y pitidos 3-2-1 al cambiar de fase.
- Diseño oscuro, un solo acento lima, todo en `LazyVerticalGrid`.

## Cómo abrirlo

1. Instala **Android Studio** (te instala el Android SDK automáticamente).
2. *File → Open* → elige la carpeta `MegaloFit`. Android Studio descargará Gradle 8.11.1 y las
   dependencias (la primera vez tarda unos minutos). Si te pide generar el *Gradle wrapper*, acepta.
3. Conecta tu móvil (con *Depuración USB* activada) o crea un emulador y pulsa ▶ Run.

Tests del motor del temporizador: `app/src/test/.../WorkoutEngineTest.kt` (clic derecho → Run).

## Estructura

```
app/src/main/java/com/megalofit/
├── data/        Room: Exercise, Routine, RoutineItem, DAOs, FitRepository
├── timer/       WorkoutEngine (lógica pura, testeable) + WorkoutTimerService (foreground)
├── ui/          Compose: rutinas, editor, catálogo, entrenamiento, tema y componentes
└── util/        Formato de tiempos y búsqueda
```

## Hoja de ruta

- **Catálogo ampliado**: importar [free-exercise-db](https://github.com/yuhonas/free-exercise-db)
  (800+ ejercicios con imágenes, dominio público, en inglés) o la API de [wger](https://wger.de)
  (multilingüe, incluye español, licencia CC-BY-SA). El catálogo ya ignora duplicados por nombre,
  así que se puede ampliar sin perder lo existente.
- **Historial** de entrenamientos (registro de cada sesión, pesos usados, progresión).
- **Música**:
  1. Fácil y sin SDK: botones play/pausa/siguiente que controlan *cualquier* reproductor
     (Spotify incluido) enviando eventos multimedia con `AudioManager.dispatchMediaKeyEvent`.
  2. Completo: Spotify App Remote SDK (requiere registrar la app en Spotify for Developers).
- **Salud**: al terminar, guardar un `ExerciseSessionRecord` en **Health Connect**. Zepp,
  Google Fit, Samsung Health, etc. se comunican con Health Connect, así que no hace falta
  integrarse con cada uno.
- Textos en `strings.xml` (ahora están en el código) para poder traducir.
