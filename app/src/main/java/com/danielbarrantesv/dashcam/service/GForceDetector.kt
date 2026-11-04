package com.danielbarrantesv.dashcam.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A single accelerometer sample (after gravity removal). */
data class AccelSample(
    val timestampMs: Long,
    val x: Float,   // lateral   (left/right)  m/s^2
    val y: Float,   // longitudinal (forward)  m/s^2
    val z: Float,   // vertical                  m/s^2
    val magnitudeG: Float
)

/**
 * Streams accelerometer samples with linear-acceleration sensor (gravity removed)
 * and detects G-force events when magnitude exceeds [thresholdG].
 *
 * For dashcam purposes we treat any acceleration above 0.4 g as an "event":
 *   - hard brake: y < -threshold
 *   - hard accel: y >  threshold
 *   - sharp left: x < -threshold
 *   - sharp right: x > threshold
 *   - impact:     magnitude > 2 * threshold
 */
@Singleton
class GForceDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sm: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val linearSensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun samples(samplePeriodUs: Int = 50_000): Flow<AccelSample> = callbackFlow {
        val sensor = linearSensor
        if (sensor == null) {
            close(IllegalStateException("No accelerometer available"))
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val magMs2 = kotlin.math.sqrt(x * x + y * y + z * z)
                val magG = magMs2 / GRAVITY
                trySend(
                    AccelSample(
                        timestampMs = System.currentTimeMillis(),
                        x = x, y = y, z = z,
                        magnitudeG = magG
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
        }

        sm.registerListener(listener, sensor, samplePeriodUs)
        awaitClose { sm.unregisterListener(listener) }
    }

    /**
     * Classifies a sample into an event type when it crosses [thresholdG].
     * Returns null if below threshold.
     */
    fun classify(sample: AccelSample, thresholdG: Float): EventType? {
        val xG = sample.x / GRAVITY
        val yG = sample.y / GRAVITY
        if (sample.magnitudeG > thresholdG * 2f) return EventType.IMPACT
        if (kotlin.math.abs(xG) > thresholdG) return if (xG > 0) EventType.TURN_RIGHT else EventType.TURN_LEFT
        if (kotlin.math.abs(yG) > thresholdG) return if (yG > 0) EventType.ACCEL else EventType.BRAKE
        return null
    }

    enum class EventType(val label: String) {
        BRAKE("Frenada brusca"),
        ACCEL("Aceleración brusca"),
        TURN_LEFT("Giro cerrado a la izquierda"),
        TURN_RIGHT("Giro cerrado a la derecha"),
        IMPACT("Impacto")
    }

    companion object {
        const val GRAVITY = 9.80665f
    }
}
