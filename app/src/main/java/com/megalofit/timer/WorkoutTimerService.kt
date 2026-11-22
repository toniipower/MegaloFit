package com.megalofit.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.megalofit.MainActivity
import com.megalofit.MegaloFitApp
import com.megalofit.R
import com.megalofit.util.ceilSeconds
import com.megalofit.util.formatClock
import com.megalofit.util.formatElapsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service que mantiene vivo el entrenamiento con la pantalla bloqueada o con
 * otra app delante. La notificación muestra la cuenta atrás (la dibuja el sistema con un
 * cronómetro, así que solo se actualiza al cambiar de paso o pausar).
 */
class WorkoutTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null
    private val engine get() = WorkoutTimer.engine

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground(buildNotification(TimerState()))
                startWorkout(intent.getLongExtra(EXTRA_ROUTINE_ID, -1L))
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> engine.toggle()
            ACTION_NEXT -> engine.next()
            ACTION_PREVIOUS -> engine.previous()
            ACTION_STOP -> {
                stopWorkout()
                return START_NOT_STICKY
            }
        }
        // Una acción suelta sin entrenamiento en marcha (p. ej. notificación antigua): no hacer nada.
        if (!engine.state.value.active || tickJob == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        tone?.release()
        tone = null
        super.onDestroy()
    }

    private fun startWorkout(routineId: Long) {
        tickJob?.cancel()
        tickJob = null
        scope.launch {
            val repo = (application as MegaloFitApp).repository
            val routine = repo.getRoutine(routineId)
            val steps = WorkoutPlan.build(repo.getItems(routineId))
            if (routine == null || steps.isEmpty()) {
                stopWorkout()
                return@launch
            }
            engine.start(routineId, routine.name, steps)
            acquireWakeLock()
            startTicking()
        }
    }

    private fun startTicking() {
        tickJob = scope.launch {
            var lastNotificationKey = ""
            var lastIndex = -1
            var lastBeepSecond = -1L
            while (isActive) {
                val changed = engine.tick()
                val s = engine.state.value
                if (!s.active) break

                if (s.index != lastIndex) {
                    lastIndex = s.index
                    lastBeepSecond = -1L
                }
                if (changed) signalPhaseChange(s.finished)

                // Pitidos de aviso 3-2-1 en los pasos cronometrados.
                val step = s.current
                if (s.running && step != null && step.isTimed) {
                    val sec = ceilSeconds(s.remainingMs)
                    if (sec in 1..3 && sec != lastBeepSecond) {
                        lastBeepSecond = sec
                        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    }
                }

                val key = "${s.index}|${s.running}|${s.finished}"
                if (key != lastNotificationKey) {
                    lastNotificationKey = key
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(s))
                }

                if (s.finished) {
                    onWorkoutFinished()
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    private fun signalPhaseChange(finished: Boolean) {
        if (finished) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1))
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 600)
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
    }

    private fun onWorkoutFinished() {
        releaseWakeLock()
        // DETACH: la notificación de "completada" se queda, pero ya no es fija.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopWorkout() {
        tickJob?.cancel()
        tickJob = null
        engine.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Notificación -----------------------------------------------------------------------

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Temporizador de entrenamiento", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Cuenta atrás de series y descansos"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, WorkoutTimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(s: TimerState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val step = s.current
        when {
            s.finished -> builder
                .setContentTitle("¡Rutina completada!")
                .setContentText("${s.routineName} · ${formatElapsed(s.elapsedMs)}")
                .setOngoing(false)
                .setAutoCancel(true)

            !s.active || step == null -> builder
                .setContentTitle("MegaloFit")
                .setContentText("Preparando entrenamiento…")
                .setOngoing(true)

            else -> {
                val isRest = step.type == PhaseType.REST
                val title = if (isRest) "Descanso" else step.exerciseName
                val detail = if (isRest) {
                    step.nextLabel.orEmpty()
                } else {
                    "Serie ${step.setNumber}/${step.totalSets}" + if (!step.isTimed) " · ${step.reps} reps" else ""
                }
                val text = when {
                    !s.running && step.isTimed -> "En pausa · ${formatClock(ceilSeconds(s.remainingMs))} · $detail"
                    !s.running -> "En pausa · $detail"
                    else -> detail
                }
                builder
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSubText("${s.index + 1}/${s.steps.size}")
                    .setOngoing(true)
                if (s.running && step.isTimed) {
                    builder
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setShowWhen(true)
                        .setWhen(System.currentTimeMillis() + s.remainingMs)
                } else {
                    builder.setShowWhen(false)
                }
                builder
                    .addAction(R.drawable.ic_stat_timer, if (s.running) "Pausa" else "Reanudar", servicePendingIntent(ACTION_TOGGLE, 1))
                    .addAction(R.drawable.ic_stat_timer, if (step.isTimed) "Saltar" else "Hecho", servicePendingIntent(ACTION_NEXT, 2))
                    .addAction(R.drawable.ic_stat_timer, "Terminar", servicePendingIntent(ACTION_STOP, 3))
            }
        }
        return builder.build()
    }

    // --- Wake lock --------------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MegaloFit:workout")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_WORKOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "workout_timer"
        private const val TICK_MS = 200L
        private const val MAX_WORKOUT_MS = 4 * 60 * 60 * 1000L

        private const val ACTION_START = "com.megalofit.timer.START"
        const val ACTION_TOGGLE = "com.megalofit.timer.TOGGLE"
        const val ACTION_NEXT = "com.megalofit.timer.NEXT"
        const val ACTION_PREVIOUS = "com.megalofit.timer.PREVIOUS"
        const val ACTION_STOP = "com.megalofit.timer.STOP"
        private const val EXTRA_ROUTINE_ID = "routine_id"

        fun start(context: Context, routineId: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WorkoutTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ROUTINE_ID, routineId),
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, WorkoutTimerService::class.java).setAction(action))
        }

        /** Cierra la pantalla de "completada": limpia el estado y la notificación. */
        fun dismiss(context: Context) {
            WorkoutTimer.engine.stop()
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}
