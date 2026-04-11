package com.dirtbike.weartracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationData
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.dirtbike.weartracker.MainActivity
import com.dirtbike.weartracker.data.TrackPoint
import com.dirtbike.weartracker.data.TrackingQualityMode
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.gpx.GpxWriter
import com.dirtbike.weartracker.gpx.RideGpxParser
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "ride_tracking"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_TRACKING = "com.dirtbike.weartracker.action.STOP_TRACKING"
        const val ACTION_PAUSE_TRACKING = "com.dirtbike.weartracker.action.PAUSE_TRACKING"
        const val ACTION_RESUME_TRACKING = "com.dirtbike.weartracker.action.RESUME_TRACKING"
        const val EXTRA_TRACKING_QUALITY_MODE = "com.dirtbike.weartracker.extra.TRACKING_QUALITY_MODE"
        private const val PREFS_NAME = "tracker_prefs"
        private const val PREF_WAS_TRACKING = "was_tracking"
        private const val PREF_TRACKING_MODE = "tracking_mode"
        private const val PREF_START_TIME_MS = "start_time_ms"
        private const val PREF_TOTAL_PAUSED_MS = "total_paused_ms"
        private const val PREF_PAUSE_STARTED_MS = "pause_started_ms"
        private const val PREF_IS_PAUSED = "is_paused"
        private const val DRAFT_SAVE_INTERVAL = 30
        private const val MAX_REASONABLE_SPEED_METERS_PER_SECOND = 45.0
        private const val GPS_SIGNAL_TIMEOUT_MS = 30_000L
        private const val GPS_STATUS_READY = "Ready to start"
        private const val GPS_STATUS_SEARCHING = "Searching for GPS..."
        private const val GPS_STATUS_LOCKED = "GPS locked"
        private const val GPS_STATUS_LOST = "GPS signal lost"
        private const val GPS_STATUS_PAUSED = "Paused"

        private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
        val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

        private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
        val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        private val _totalDistanceMeters = MutableStateFlow(0.0)
        val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

        private val _currentBearing = MutableStateFlow(0f)
        val currentBearing: StateFlow<Float> = _currentBearing.asStateFlow()

        private val _bearingToStart = MutableStateFlow<Float?>(null)
        val bearingToStart: StateFlow<Float?> = _bearingToStart.asStateFlow()

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _hasGpsFix = MutableStateFlow(false)
        val hasGpsFix: StateFlow<Boolean> = _hasGpsFix.asStateFlow()

        private val _gpsStatus = MutableStateFlow(GPS_STATUS_READY)
        val gpsStatus: StateFlow<String> = _gpsStatus.asStateFlow()

        fun wasInterruptedByBoot(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_WAS_TRACKING, false)
        }

        fun clearInterruptedFlag(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_WAS_TRACKING, false).apply()
        }

        fun addWaypoint(waypoint: Waypoint) {
            _waypoints.value = _waypoints.value + waypoint
        }

        fun reset() {
            _trackPoints.value = emptyList()
            _waypoints.value = emptyList()
            _elapsedSeconds.value = 0L
            _totalDistanceMeters.value = 0.0
            _currentBearing.value = 0f
            _bearingToStart.value = null
            _isTracking.value = false
            _isPaused.value = false
            _hasGpsFix.value = false
            _gpsStatus.value = GPS_STATUS_READY
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var usingHealthServices = false
    private lateinit var wakeLock: PowerManager.WakeLock
    private var timerThread: Thread? = null
    private var startTimeMs = 0L
    private var rideClockStarted = false
    private var totalPausedDurationMs = 0L
    private var pauseStartedMs = 0L
    private var lastGpsFixTimeMs = 0L
    private var hasEverLockedGps = false
    private var nextPointStartsNewSegment = false
    private val recentRawPoints = ArrayDeque<TrackPoint>()
    private var modeConfig = TrackingModeConfig.forMode(TrackingQualityMode.MEDIUM)
    private var explicitStopRequested = false

    private val exerciseClient by lazy {
        HealthServices.getClient(this).exerciseClient
    }

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val locationPoints = update.latestMetrics.getData(DataType.LOCATION)
            for (point in locationPoints) {
                val loc: LocationData = point.value
                processLocation(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    alt = if (loc.altitude > Double.MIN_VALUE / 2) loc.altitude else 0.0,
                    rawBearing = if (loc.bearing >= 0) loc.bearing else -1.0
                )
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            scope.launch(Dispatchers.Main) { startFusedFallback() }
        }

        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: androidx.health.services.client.data.Availability
        ) = Unit
    }

    private var fusedCallback: LocationCallback? = null
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING) {
            explicitStopRequested = true
            TrackingDebugLog.write(this, "stop requested by user")
            clearPersistedSession()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PAUSE_TRACKING) {
            pauseTracking()
            return START_STICKY
        }

        if (intent?.action == ACTION_RESUME_TRACKING) {
            resumeTracking()
            return START_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (_isTracking.value) {
            TrackingDebugLog.write(this, "onStartCommand ignored: already tracking")
            return START_STICKY
        }
        if (!hasTrackingPermissions()) {
            TrackingDebugLog.write(this, "onStartCommand stopping: missing permissions")
            explicitStopRequested = true
            stopSelf()
            return START_NOT_STICKY
        }
        recentRawPoints.clear()
        val selectedMode = resolveTrackingMode(intent)
        modeConfig = TrackingModeConfig.forMode(selectedMode)
        persistTrackingMode(selectedMode)
        val shouldRecover = intent == null && wasInterruptedByBoot(this)
        TrackingDebugLog.write(
            this,
            "tracking start mode=${modeConfig.mode.name} restarted=${intent == null} recover=$shouldRecover"
        )
        GpxWriter.enableDraftWrites()
        val recovered = if (shouldRecover) restoreDraftState() else false
        if (!recovered) {
            reset()
        }
        _isTracking.value = true
        if (!recovered) {
            rideClockStarted = false
            totalPausedDurationMs = 0L
            pauseStartedMs = 0L
            lastGpsFixTimeMs = 0L
            hasEverLockedGps = false
            nextPointStartsNewSegment = false
        }
        if (recovered) {
            restoreTimingFromTrack()
        }
        if (!_isPaused.value) {
            _gpsStatus.value = GPS_STATUS_SEARCHING
        }
        persistTrackingActive(true)
        acquireWakeLock()
        startLocationProvider()
        return START_STICKY
    }

    private fun startLocationProvider() {
        if (modeConfig.preferHealthServices) {
            TrackingDebugLog.write(this, "location provider: health-services")
            startHealthServicesOrFallback()
        } else {
            TrackingDebugLog.write(this, "location provider: fused")
            startFusedFallback()
        }
    }

    private fun pauseTracking() {
        if (!_isTracking.value) {
            stopSelf()
            return
        }
        if (_isPaused.value) return
        val now = System.currentTimeMillis()
        _elapsedSeconds.value = activeElapsedSeconds(now)
        _isPaused.value = true
        pauseStartedMs = now
        _gpsStatus.value = GPS_STATUS_PAUSED
        persistSessionState()
        saveDraftAsync()
        refreshNotification()
        TrackingDebugLog.write(this, "tracking paused elapsed=${_elapsedSeconds.value}")
    }

    private fun resumeTracking() {
        if (!_isTracking.value) {
            stopSelf()
            return
        }
        if (!_isPaused.value) return
        val now = System.currentTimeMillis()
        if (pauseStartedMs > 0L) {
            totalPausedDurationMs += (now - pauseStartedMs).coerceAtLeast(0L)
        }
        pauseStartedMs = 0L
        _isPaused.value = false
        nextPointStartsNewSegment = _trackPoints.value.isNotEmpty()
        recentRawPoints.clear()
        _gpsStatus.value = if (now - lastGpsFixTimeMs <= GPS_SIGNAL_TIMEOUT_MS) {
            GPS_STATUS_LOCKED
        } else {
            GPS_STATUS_SEARCHING
        }
        persistSessionState()
        refreshNotification()
        TrackingDebugLog.write(this, "tracking resumed elapsed=${_elapsedSeconds.value}")
    }

    private fun startHealthServicesOrFallback() {
        scope.launch {
            try {
                try {
                    val currentInfo = exerciseClient.getCurrentExerciseInfoAsync().await()
                    if (currentInfo.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                        exerciseClient.endExerciseAsync().await()
                        kotlinx.coroutines.delay(300L)
                    }
                } catch (_: Exception) {
                }

                val caps = exerciseClient.getCapabilitiesAsync().await()
                val locationSupported = caps.supportedExerciseTypes.contains(ExerciseType.WORKOUT) &&
                    caps.getExerciseTypeCapabilities(ExerciseType.WORKOUT)
                        .supportedDataTypes
                        .contains(DataType.LOCATION)

                if (locationSupported) {
                    exerciseClient.setUpdateCallback(
                        ContextCompat.getMainExecutor(this@TrackingService),
                        exerciseCallback
                    )

                    val config = ExerciseConfig.builder(ExerciseType.WORKOUT)
                        .setDataTypes(setOf(DataType.LOCATION))
                        .setIsGpsEnabled(true)
                        .setIsAutoPauseAndResumeEnabled(false)
                        .build()
                    exerciseClient.startExerciseAsync(config).await()
                    usingHealthServices = true
                    TrackingDebugLog.write(this@TrackingService, "health-services started")
                } else {
                    TrackingDebugLog.write(this@TrackingService, "health-services unsupported; using fused fallback")
                    withContext(Dispatchers.Main) { startFusedFallback() }
                }
            } catch (_: Exception) {
                TrackingDebugLog.write(this@TrackingService, "health-services failed; using fused fallback")
                withContext(Dispatchers.Main) { startFusedFallback() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFusedFallback() {
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    if (loc.accuracy > modeConfig.maxAcceptedAccuracyMeters) return
                    processLocation(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        alt = loc.altitude,
                        rawBearing = if (loc.hasBearing() && loc.speed > 0.5f) loc.bearing.toDouble() else -1.0
                    )
                }
            }
        }
        fusedCallback = cb
        val request = LocationRequest.Builder(modeConfig.fusedPriority, modeConfig.desiredIntervalMs)
            .setMinUpdateIntervalMillis(modeConfig.minIntervalMs)
            .setMinUpdateDistanceMeters(modeConfig.fusedMinDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()
        TrackingDebugLog.write(
            this,
            "fused request interval=${modeConfig.desiredIntervalMs} minInterval=${modeConfig.minIntervalMs} minDistance=${modeConfig.fusedMinDistanceMeters}"
        )
        fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    private fun processLocation(lat: Double, lon: Double, alt: Double, rawBearing: Double) {
        val currentPoints = _trackPoints.value
        val now = System.currentTimeMillis()
        val hadGpsFix = _hasGpsFix.value
        val gpsWasLost = _gpsStatus.value == GPS_STATUS_LOST
        lastGpsFixTimeMs = now
        _hasGpsFix.value = true
        if (!_isPaused.value) {
            _gpsStatus.value = GPS_STATUS_LOCKED
        }

        if (!hadGpsFix) {
            if (gpsWasLost && hasEverLockedGps) {
                TrackingDebugLog.write(this, "gps restored")
                vibrateGpsRestored()
            } else if (!hasEverLockedGps) {
                hasEverLockedGps = true
                TrackingDebugLog.write(this, "gps first lock")
                vibrateGpsFirstLock()
            }
        }

        if (_isPaused.value) {
            return
        }

        val segmentId = when {
            currentPoints.isEmpty() -> 0
            nextPointStartsNewSegment -> currentPoints.last().segmentId + 1
            else -> currentPoints.last().segmentId
        }
        val newPoint = smoothTrackPoint(TrackPoint(lat, lon, alt, now, segmentId))

        if (currentPoints.isEmpty()) {
            beginTrackingFromFirstFix(newPoint.timestampMs)
            _trackPoints.value = listOf(newPoint)
            saveDraftAsync()
            if (rawBearing >= 0) {
                _currentBearing.value = rawBearing.toFloat()
            }
            return
        }

        val prev = currentPoints.last()
        val deltaMeters = haversineMeters(
            prev.latitude,
            prev.longitude,
            newPoint.latitude,
            newPoint.longitude
        )
        val elapsedSeconds = ((newPoint.timestampMs - prev.timestampMs).coerceAtLeast(250L)) / 1000.0
        val speedMetersPerSecond = deltaMeters / elapsedSeconds

        if (prev.segmentId != newPoint.segmentId) {
            nextPointStartsNewSegment = false
            _bearingToStart.value = bearingTo(
                newPoint.latitude,
                newPoint.longitude,
                currentPoints.first().latitude,
                currentPoints.first().longitude
            )
            _trackPoints.value = currentPoints + newPoint
            saveDraftAsync()
            TrackingDebugLog.write(this, "new segment started id=${newPoint.segmentId}")
            return
        }

        if (speedMetersPerSecond > MAX_REASONABLE_SPEED_METERS_PER_SECOND) {
            if (recentRawPoints.isNotEmpty()) {
                recentRawPoints.removeLast()
            }
            return
        }

        if (rawBearing >= 0) {
            _currentBearing.value = rawBearing.toFloat()
        } else if (deltaMeters >= modeConfig.minDistanceForNewPointMeters) {
            _currentBearing.value = bearingTo(
                prev.latitude,
                prev.longitude,
                newPoint.latitude,
                newPoint.longitude
            )
        }

        _bearingToStart.value = bearingTo(
            newPoint.latitude,
            newPoint.longitude,
            currentPoints.first().latitude,
            currentPoints.first().longitude
        )

        if (deltaMeters < modeConfig.minDistanceForNewPointMeters) {
            return
        }

        _totalDistanceMeters.value += deltaMeters
        val updatedPoints = currentPoints + newPoint
        _trackPoints.value = updatedPoints

        if (updatedPoints.size % DRAFT_SAVE_INTERVAL == 0) {
            saveDraftAsync()
        }
    }

    override fun onDestroy() {
        val finalTrackPoints = _trackPoints.value
        val finalWaypoints = _waypoints.value
        TrackingDebugLog.write(
            this,
            "service destroy explicit=$explicitStopRequested points=${finalTrackPoints.size} waypoints=${finalWaypoints.size}"
        )
        _isTracking.value = false
        if (explicitStopRequested) {
            clearPersistedSession()
        }

        if (usingHealthServices) {
            scope.launch {
                try {
                    exerciseClient.endExerciseAsync().await()
                } catch (_: Exception) {
                }
                try {
                    exerciseClient.clearUpdateCallbackAsync(exerciseCallback).await()
                } catch (_: Exception) {
                }
            }
        }

        fusedCallback?.let { fusedClient.removeLocationUpdates(it) }
        timerThread?.interrupt()
        recentRawPoints.clear()

        if (finalTrackPoints.isNotEmpty()) {
            runCatching {
                GpxWriter.writeDraft(applicationContext, finalTrackPoints, finalWaypoints)
            }
        }

        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()

        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        TrackingDebugLog.write(this, "task removed while tracking=${_isTracking.value}")
        super.onTaskRemoved(rootIntent)
    }

    private fun startTimer() {
        timerThread = Thread {
            while (_isTracking.value && !Thread.currentThread().isInterrupted) {
                val now = System.currentTimeMillis()
                if (rideClockStarted) {
                    _elapsedSeconds.value = activeElapsedSeconds(now)
                    if (
                        !_isPaused.value &&
                        lastGpsFixTimeMs > 0L &&
                        now - lastGpsFixTimeMs > GPS_SIGNAL_TIMEOUT_MS &&
                        _gpsStatus.value != GPS_STATUS_LOST
                    ) {
                        _hasGpsFix.value = false
                        _gpsStatus.value = GPS_STATUS_LOST
                        TrackingDebugLog.write(this@TrackingService, "gps lost after timeout")
                        vibrateGpsLost()
                    }
                }
                try {
                    Thread.sleep(1_000L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WearTracker:TrackingWakeLock"
        )
        wakeLock.acquire(8 * 60 * 60 * 1000L)
    }

    private fun persistTrackingActive(active: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(PREF_WAS_TRACKING, active).apply()
    }

    private fun persistSessionState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putLong(PREF_START_TIME_MS, startTimeMs)
            .putLong(PREF_TOTAL_PAUSED_MS, totalPausedDurationMs)
            .putLong(PREF_PAUSE_STARTED_MS, pauseStartedMs)
            .putBoolean(PREF_IS_PAUSED, _isPaused.value)
            .apply()
    }

    private fun clearPersistedSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_WAS_TRACKING, false)
            .putBoolean(PREF_IS_PAUSED, false)
            .remove(PREF_START_TIME_MS)
            .remove(PREF_TOTAL_PAUSED_MS)
            .remove(PREF_PAUSE_STARTED_MS)
            .apply()
    }

    private fun persistTrackingMode(mode: TrackingQualityMode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(PREF_TRACKING_MODE, mode.name).apply()
    }

    private fun resolveTrackingMode(intent: Intent?): TrackingQualityMode {
        val requestedMode = intent?.getStringExtra(EXTRA_TRACKING_QUALITY_MODE)
        val savedMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_TRACKING_MODE, null)
        return TrackingQualityMode.fromName(requestedMode ?: savedMode)
    }

    private fun restoreDraftState(): Boolean {
        val draft = GpxWriter.getDraftFile(this) ?: run {
            TrackingDebugLog.write(this, "recovery skipped: no draft")
            return false
        }
        val parsed = runCatching { RideGpxParser.readRide(draft) }.getOrNull() ?: run {
            TrackingDebugLog.write(this, "recovery failed: draft parse error")
            return false
        }
        if (parsed.trackPoints.isEmpty()) {
            TrackingDebugLog.write(this, "recovery skipped: empty draft")
            return false
        }

        _trackPoints.value = parsed.trackPoints
        _waypoints.value = parsed.waypoints
        _totalDistanceMeters.value = calculateDistanceMeters(parsed.trackPoints)
        updateBearingsAfterRestore(parsed.trackPoints)
        TrackingDebugLog.write(
            this,
            "recovery restored points=${parsed.trackPoints.size} waypoints=${parsed.waypoints.size}"
        )
        return true
    }

    private fun restoreTimingFromTrack() {
        val points = _trackPoints.value
        val first = points.firstOrNull() ?: return
        val last = points.lastOrNull() ?: first
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        rideClockStarted = true
        startTimeMs = prefs.getLong(PREF_START_TIME_MS, first.timestampMs)
        lastGpsFixTimeMs = last.timestampMs
        totalPausedDurationMs = prefs.getLong(PREF_TOTAL_PAUSED_MS, calculatePausedDurationFromSegments(points))
        pauseStartedMs = prefs.getLong(PREF_PAUSE_STARTED_MS, 0L)
        val restoredPaused = prefs.getBoolean(PREF_IS_PAUSED, false)
        if (restoredPaused && pauseStartedMs == 0L) {
            pauseStartedMs = now
        }
        hasEverLockedGps = true
        _isPaused.value = restoredPaused
        _elapsedSeconds.value = activeElapsedSeconds(now)
        _hasGpsFix.value = false
        _gpsStatus.value = if (restoredPaused) GPS_STATUS_PAUSED else GPS_STATUS_SEARCHING
        startTimer()
    }

    private fun updateBearingsAfterRestore(points: List<TrackPoint>) {
        if (points.size >= 2) {
            val previous = points[points.lastIndex - 1]
            val current = points.last()
            _currentBearing.value = bearingTo(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude
            )
        }

        val first = points.firstOrNull()
        val last = points.lastOrNull()
        if (first != null && last != null && first != last) {
            _bearingToStart.value = bearingTo(
                last.latitude,
                last.longitude,
                first.latitude,
                first.longitude
            )
        }
    }

    private fun calculateDistanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var distance = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId != current.segmentId) continue
            distance += haversineMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude
            )
        }
        return distance
    }

    private fun calculatePausedDurationFromSegments(points: List<TrackPoint>): Long {
        if (points.size < 2) return 0L
        var pausedDuration = 0L
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId != current.segmentId) {
                pausedDuration += (current.timestampMs - previous.timestampMs).coerceAtLeast(0L)
            }
        }
        return pausedDuration
    }

    private fun activeElapsedSeconds(nowMs: Long): Long {
        if (!rideClockStarted || startTimeMs <= 0L) return 0L
        val currentPauseMs = if (_isPaused.value && pauseStartedMs > 0L) {
            (nowMs - pauseStartedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return ((nowMs - startTimeMs - totalPausedDurationMs - currentPauseMs).coerceAtLeast(0L)) / 1000L
    }

    private fun saveDraftAsync() {
        scope.launch(Dispatchers.IO) {
            GpxWriter.writeDraft(applicationContext, _trackPoints.value, _waypoints.value)
            TrackingDebugLog.write(applicationContext, "draft saved points=${_trackPoints.value.size}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while GPS tracking is active"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        if (!_isTracking.value) return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (_isPaused.value) "Ride paused" else "Tracking your ride")
            .setContentText(if (_isPaused.value) "Paused - tap to return" else "GPS active - tap to return")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val ongoingStatus = Status.Builder()
            .addTemplate(if (_isPaused.value) "Ride paused" else "Ride tracking")
            .build()

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_menu_mylocation)
            .setTouchIntent(contentIntent)
            .setStatus(ongoingStatus)
            .setTitle("Ride Tracker")
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun beginTrackingFromFirstFix(timestampMs: Long) {
        if (rideClockStarted) return
        rideClockStarted = true
        startTimeMs = timestampMs
        _elapsedSeconds.value = 0L
        persistSessionState()
        startTimer()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun smoothTrackPoint(rawPoint: TrackPoint): TrackPoint {
        recentRawPoints.addLast(rawPoint)
        while (recentRawPoints.size > modeConfig.rawSmoothingWindowSize) {
            recentRawPoints.removeFirst()
        }

        val sampleCount = recentRawPoints.size.toDouble()
        return TrackPoint(
            latitude = recentRawPoints.sumOf { it.latitude } / sampleCount,
            longitude = recentRawPoints.sumOf { it.longitude } / sampleCount,
            altitude = recentRawPoints.sumOf { it.altitude } / sampleCount,
            timestampMs = rawPoint.timestampMs
        )
    }

    private fun vibrateGpsFirstLock() {
        vibratePattern(longArrayOf(0L, 70L, 40L, 110L))
    }

    private fun vibrateGpsRestored() {
        vibratePattern(longArrayOf(0L, 80L, 35L, 80L))
    }

    private fun vibrateGpsLost() {
        vibratePattern(longArrayOf(0L, 140L, 60L, 140L))
    }

    private fun vibratePattern(timings: LongArray) {
        val vibrator = getWatchVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun getWatchVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun hasTrackingPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private data class TrackingModeConfig(
        val mode: TrackingQualityMode,
        val preferHealthServices: Boolean,
        val fusedPriority: Int,
        val desiredIntervalMs: Long,
        val minIntervalMs: Long,
        val fusedMinDistanceMeters: Float,
        val minDistanceForNewPointMeters: Double,
        val rawSmoothingWindowSize: Int,
        val maxAcceptedAccuracyMeters: Float
    ) {
        companion object {
            fun forMode(mode: TrackingQualityMode): TrackingModeConfig {
                return when (mode) {
                    TrackingQualityMode.LOW -> TrackingModeConfig(
                        mode = mode,
                        preferHealthServices = false,
                        fusedPriority = Priority.PRIORITY_HIGH_ACCURACY,
                        desiredIntervalMs = 5_000L,
                        minIntervalMs = 2_500L,
                        fusedMinDistanceMeters = 8f,
                        minDistanceForNewPointMeters = 8.0,
                        rawSmoothingWindowSize = 7,
                        maxAcceptedAccuracyMeters = 50f
                    )

                    TrackingQualityMode.MEDIUM -> TrackingModeConfig(
                        mode = mode,
                        preferHealthServices = true,
                        fusedPriority = Priority.PRIORITY_HIGH_ACCURACY,
                        desiredIntervalMs = 1_000L,
                        minIntervalMs = 500L,
                        fusedMinDistanceMeters = 3f,
                        minDistanceForNewPointMeters = 3.0,
                        rawSmoothingWindowSize = 5,
                        maxAcceptedAccuracyMeters = 30f
                    )

                    TrackingQualityMode.HIGH -> TrackingModeConfig(
                        mode = mode,
                        preferHealthServices = false,
                        fusedPriority = Priority.PRIORITY_HIGH_ACCURACY,
                        desiredIntervalMs = 500L,
                        minIntervalMs = 250L,
                        fusedMinDistanceMeters = 0f,
                        minDistanceForNewPointMeters = 3.0,
                        rawSmoothingWindowSize = 3,
                        maxAcceptedAccuracyMeters = 25f
                    )
                }
            }
        }
    }
}
