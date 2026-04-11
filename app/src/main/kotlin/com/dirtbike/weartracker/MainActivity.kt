package com.dirtbike.weartracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.dirtbike.weartracker.ui.AmbientScreen
import com.dirtbike.weartracker.ui.HomeScreen
import com.dirtbike.weartracker.ui.MarkSpotScreen
import com.dirtbike.weartracker.ui.RideNameScreen
import com.dirtbike.weartracker.ui.SaveConfirmScreen
import com.dirtbike.weartracker.ui.SavedRideMapScreen
import com.dirtbike.weartracker.ui.SavedRidesScreen
import com.dirtbike.weartracker.ui.TrackingScreen
import com.dirtbike.weartracker.ui.theme.WearTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingRideStart = false
    private lateinit var sensorManager: SensorManager
    private var headingSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val trackingPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val allGranted = trackingPermissions.all { permission ->
            grantResults[permission] == true || hasPermission(permission)
        }
        if (allGranted && pendingRideStart) {
            viewModel.startRide()
        } else if (pendingRideStart) {
            Toast.makeText(
                this,
                "Location and activity permissions are required to track rides.",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingRideStart = false
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            viewModel.setAmbient(true)
        }

        override fun onUpdateAmbient() = Unit

        override fun onExitAmbient() {
            viewModel.setAmbient(false)
        }
    }

    private val headingListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rawHeading = normalizeDegrees(
                Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            )
            viewModel.setCompassHeading(smoothHeading(viewModel.compassHeading.value, rawHeading))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private lateinit var ambientObserver: AmbientLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        headingSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)

        setContent {
            WearTrackerTheme {
                val screen by viewModel.screen.collectAsState()
                val isAmbient by viewModel.isAmbient.collectAsState()
                val trackPoints by viewModel.trackPoints.collectAsState()
                val waypoints by viewModel.waypoints.collectAsState()
                val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
                val totalDistanceMeters by viewModel.totalDistanceMeters.collectAsState()
                val movementBearing by viewModel.currentBearing.collectAsState()
                val bearingToStart by viewModel.bearingToStart.collectAsState()
                val isTracking by viewModel.isTracking.collectAsState()
                val isPaused by viewModel.isPaused.collectAsState()
                val hasGpsFix by viewModel.hasGpsFix.collectAsState()
                val gpsStatus by viewModel.gpsStatus.collectAsState()
                val compassHeading by viewModel.compassHeading.collectAsState()
                val isMapZoomedIn by viewModel.isMapZoomedIn.collectAsState()
                val trackingPage by viewModel.trackingPage.collectAsState()
                val importedWaypoints by viewModel.importedWaypoints.collectAsState()
                val activeWaypoint by viewModel.activeWaypoint.collectAsState()
                val activeWaypointIndex by viewModel.activeWaypointIndex.collectAsState()
                val savedRides by viewModel.savedRides.collectAsState()
                val isSavedRidesLoading by viewModel.isSavedRidesLoading.collectAsState()
                val selectedRideMap by viewModel.selectedRideMap.collectAsState()
                val pendingRideName by viewModel.pendingRideName.collectAsState()
                val trackingQualityMode by viewModel.trackingQualityMode.collectAsState()
                val currentTimeMs by produceState(initialValue = System.currentTimeMillis()) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(1_000L)
                    }
                }
                val batteryPercentage by produceState(initialValue = readBatteryPercentage()) {
                    while (true) {
                        value = readBatteryPercentage()
                        delay(30_000L)
                    }
                }

                val elapsedFmt = viewModel.formatElapsed(elapsedSeconds)
                val distanceFmt = viewModel.formatDistance(totalDistanceMeters)
                val clockTimeFmt = viewModel.formatClockTime(currentTimeMs)
                val ambientGpsStatus = when {
                    isPaused -> "PAUSED"
                    gpsStatus.contains("lost", ignoreCase = true) -> "GPS LOST"
                    hasGpsFix -> "GPS LOCKED"
                    else -> "GPS SEARCHING"
                }
                val visibleScreen = if (screen == Screen.HOME && isTracking) {
                    Screen.TRACKING
                } else {
                    screen
                }

                LaunchedEffect(isTracking) {
                    if (!isTracking) {
                        viewModel.zoomMapOut()
                    }
                }

                BackHandler(enabled = visibleScreen != Screen.HOME) {
                    when (visibleScreen) {
                        Screen.SAVED_RIDE_MAP -> viewModel.closeSavedRideMap()
                        Screen.SAVED_RIDES -> viewModel.navigateHome()
                        Screen.MARK_SPOT -> viewModel.cancelMarkSpot()
                        Screen.NAME_RIDE -> viewModel.cancelRideNaming()
                        Screen.TRACKING -> {
                            if (trackingPage == TrackingPage.WAYPOINT) {
                                viewModel.setTrackingPage(TrackingPage.MAP)
                            }
                        }
                        Screen.SAVE_CONFIRM -> Unit
                        Screen.HOME -> Unit
                    }
                }

                if (isAmbient && visibleScreen == Screen.TRACKING) {
                    AmbientScreen(
                        currentTimeFormatted = clockTimeFmt,
                        elapsedFormatted = elapsedFmt,
                        distanceFormatted = distanceFmt,
                        gpsStatusText = ambientGpsStatus,
                        batteryPercentage = batteryPercentage
                    )
                    return@WearTrackerTheme
                }

                val hasDraft by viewModel.hasDraft.collectAsState()

                when (visibleScreen) {
                    Screen.HOME -> HomeScreen(
                        currentTimeFormatted = clockTimeFmt,
                        trackingModeLabel = trackingQualityMode.label,
                        trackingModeDescription = trackingQualityMode.shortDescription,
                        onStart = { startRideOrRequestPermissions() },
                        onCycleTrackingMode = { viewModel.cycleTrackingQualityMode() },
                        onSavedRides = { viewModel.navigateToSavedRides() },
                        hasDraft = hasDraft,
                        onRecoverDraft = { viewModel.recoverDraft() },
                        onDiscardDraft = { viewModel.discardDraft() }
                    )

                    Screen.TRACKING -> TrackingScreen(
                        movementBearing = movementBearing,
                        heading = compassHeading,
                        bearingToStart = bearingToStart,
                        elapsedFormatted = elapsedFmt,
                        currentTimeFormatted = clockTimeFmt,
                        distanceFormatted = distanceFmt,
                        isPaused = isPaused,
                        hasGpsFix = hasGpsFix,
                        gpsStatus = gpsStatus,
                        trackPoints = trackPoints,
                        waypoints = waypoints,
                        importedWaypoints = importedWaypoints,
                        activeWaypoint = activeWaypoint,
                        activeWaypointIndex = activeWaypointIndex,
                        trackingPage = trackingPage,
                        isMapZoomedIn = isMapZoomedIn,
                        onZoomIn = { viewModel.zoomMapIn() },
                        onZoomOut = { viewModel.zoomMapOut() },
                        onTrackingPageChange = { page -> viewModel.setTrackingPage(page) },
                        onSelectWaypoint = { index -> viewModel.selectImportedWaypoint(index) },
                        onPauseResume = { viewModel.onPauseResumeTapped() },
                        onStop = { viewModel.onStopTapped() },
                        onMark = { viewModel.onMarkSpotTapped() }
                    )

                    Screen.MARK_SPOT -> MarkSpotScreen(
                        onConfirm = { name -> viewModel.confirmMarkSpot(name) },
                        onCancel = { viewModel.cancelMarkSpot() }
                    )

                    Screen.NAME_RIDE -> RideNameScreen(
                        initialName = pendingRideName,
                        onConfirm = { name -> viewModel.confirmRideName(name) },
                        onCancel = { viewModel.cancelRideNaming() }
                    )

                    Screen.SAVE_CONFIRM -> SaveConfirmScreen(
                        elapsedFormatted = elapsedFmt,
                        distanceFormatted = distanceFmt,
                        waypointCount = waypoints.size,
                        onSave = { viewModel.promptToNameRide() },
                        onDiscard = { viewModel.discardRide() }
                    )

                    Screen.SAVED_RIDES -> SavedRidesScreen(
                        rides = savedRides,
                        isLoading = isSavedRidesLoading,
                        onSendToPhone = { ride ->
                            lifecycleScope.launch {
                                Toast.makeText(this@MainActivity, "Sending GPX to phone...", Toast.LENGTH_SHORT).show()
                                val result = viewModel.sendRideToPhone(ride)
                                Toast.makeText(
                                    this@MainActivity,
                                    result.message,
                                    if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onOpenMap = { ride -> viewModel.openSavedRideMap(ride) },
                        onRename = { ride -> viewModel.promptToRenameRide(ride) },
                        onDelete = { ride -> viewModel.deleteRide(ride) },
                        onBack = { viewModel.navigateHome() }
                    )

                    Screen.SAVED_RIDE_MAP -> selectedRideMap?.let { rideMap ->
                        SavedRideMapScreen(
                            rideMap = rideMap,
                            onBack = { viewModel.closeSavedRideMap() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeTrackingIfActive()
        headingSensor?.let {
            sensorManager.registerListener(headingListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(headingListener)
        super.onPause()
    }

    override fun onDestroy() {
        lifecycle.removeObserver(ambientObserver)
        sensorManager.unregisterListener(headingListener)
        super.onDestroy()
    }

    private fun startRideOrRequestPermissions() {
        if (hasTrackingPermissions()) {
            viewModel.startRide()
            return
        }
        pendingRideStart = true
        permissionLauncher.launch(trackingPermissions)
    }

    private fun hasTrackingPermissions(): Boolean {
        return trackingPermissions.all(::hasPermission)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun smoothHeading(previous: Float?, target: Float): Float {
        if (previous == null) return target
        val delta = ((target - previous + 540f) % 360f) - 180f
        return normalizeDegrees(previous + delta * 0.2f)
    }

    private fun readBatteryPercentage(): Int? {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (batteryLevel >= 0 && batteryScale > 0) {
            return ((batteryLevel * 100f) / batteryScale).toInt().coerceIn(0, 100)
        }

        val batteryManager = getSystemService(BATTERY_SERVICE) as? BatteryManager ?: return null
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return batteryPercent.takeIf { it in 0..100 }
    }
}
