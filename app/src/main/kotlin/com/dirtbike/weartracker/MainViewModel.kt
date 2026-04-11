package com.dirtbike.weartracker

import android.app.Application
import android.content.Intent
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dirtbike.weartracker.data.ImportedGpxRepository
import com.dirtbike.weartracker.data.RideRepository
import com.dirtbike.weartracker.data.RideMapDetails
import com.dirtbike.weartracker.data.RideSummary
import com.dirtbike.weartracker.data.TrackingQualityMode
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.gpx.GpxWriter
import com.dirtbike.weartracker.gpx.RideGpxParser
import com.dirtbike.weartracker.service.TrackingService
import com.dirtbike.weartracker.transfer.PhoneTransferClient
import com.dirtbike.weartracker.transfer.PhoneTransferResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    HOME,
    TRACKING,
    MARK_SPOT,
    NAME_RIDE,
    SAVE_CONFIRM,
    SAVED_RIDES,
    SAVED_RIDE_MAP
}

enum class TrackingPage {
    MAP,
    WAYPOINT;

    companion object {
        fun fromName(value: String?): TrackingPage {
            return entries.firstOrNull { it.name == value } ?: MAP
        }
    }
}

private enum class RideNamingMode {
    SAVE_NEW,
    RENAME_EXISTING
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    val trackPoints = TrackingService.trackPoints
    val waypoints = TrackingService.waypoints
    val elapsedSeconds = TrackingService.elapsedSeconds
    val totalDistanceMeters = TrackingService.totalDistanceMeters
    val currentBearing = TrackingService.currentBearing
    val bearingToStart = TrackingService.bearingToStart
    val isTracking = TrackingService.isTracking
    val isPaused = TrackingService.isPaused
    val hasGpsFix = TrackingService.hasGpsFix
    val gpsStatus = TrackingService.gpsStatus

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    private val _compassHeading = MutableStateFlow<Float?>(null)
    val compassHeading: StateFlow<Float?> = _compassHeading.asStateFlow()

    private val _isMapZoomedIn = MutableStateFlow(false)
    val isMapZoomedIn: StateFlow<Boolean> = _isMapZoomedIn.asStateFlow()

    private val _trackingPage = MutableStateFlow(
        if (isTracking.value) {
            TrackingPage.fromName(settings.getString(PREF_ACTIVE_TRACKING_PAGE, null))
        } else {
            TrackingPage.MAP
        }
    )
    val trackingPage: StateFlow<TrackingPage> = _trackingPage.asStateFlow()

    private val _importedWaypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val importedWaypoints: StateFlow<List<Waypoint>> = _importedWaypoints.asStateFlow()

    private val _hiddenWaypointCount = MutableStateFlow(0)
    val hiddenWaypointCount: StateFlow<Int> = _hiddenWaypointCount.asStateFlow()

    private val _activeWaypoint = MutableStateFlow<Waypoint?>(null)
    val activeWaypoint: StateFlow<Waypoint?> = _activeWaypoint.asStateFlow()

    private val _activeWaypointIndex = MutableStateFlow(NO_ACTIVE_WAYPOINT)
    val activeWaypointIndex: StateFlow<Int> = _activeWaypointIndex.asStateFlow()

    private val _savedRides = MutableStateFlow<List<RideSummary>>(emptyList())
    val savedRides: StateFlow<List<RideSummary>> = _savedRides.asStateFlow()

    private val _isSavedRidesLoading = MutableStateFlow(false)
    val isSavedRidesLoading: StateFlow<Boolean> = _isSavedRidesLoading.asStateFlow()

    private val _selectedRideMap = MutableStateFlow<RideMapDetails?>(null)
    val selectedRideMap: StateFlow<RideMapDetails?> = _selectedRideMap.asStateFlow()

    private val _hasDraft = MutableStateFlow(false)
    val hasDraft: StateFlow<Boolean> = _hasDraft.asStateFlow()

    private val _pendingRideName = MutableStateFlow("")
    val pendingRideName: StateFlow<String> = _pendingRideName.asStateFlow()

    private val _trackingQualityMode = MutableStateFlow(
        TrackingQualityMode.fromName(settings.getString(PREF_TRACKING_MODE, null))
    )
    val trackingQualityMode: StateFlow<TrackingQualityMode> = _trackingQualityMode.asStateFlow()

    private data class PendingWaypoint(val lat: Double, val lon: Double, val alt: Double)

    private var pendingWaypoint: PendingWaypoint? = null
    private var rideNamingMode = RideNamingMode.SAVE_NEW
    private var rideBeingRenamed: RideSummary? = null

    init {
        checkForDraft()
        refreshImportedWaypoints()
    }

    fun setAmbient(ambient: Boolean) {
        _isAmbient.value = ambient
    }

    fun setCompassHeading(heading: Float?) {
        _compassHeading.value = heading
    }

    fun resumeTrackingIfActive() {
        if (isTracking.value && _screen.value == Screen.HOME) {
            _trackingPage.value = TrackingPage.fromName(
                settings.getString(PREF_ACTIVE_TRACKING_PAGE, null)
            )
            _screen.value = Screen.TRACKING
        }
        refreshImportedWaypoints()
    }

    fun zoomMapIn() {
        _isMapZoomedIn.value = true
    }

    fun zoomMapOut() {
        _isMapZoomedIn.value = false
    }

    fun setTrackingPage(page: TrackingPage) {
        if (_trackingPage.value == page) return
        _trackingPage.value = page
        if (page == TrackingPage.WAYPOINT) {
            refreshImportedWaypoints()
        }
        if (isTracking.value) {
            settings.edit()
                .putString(PREF_ACTIVE_TRACKING_PAGE, page.name)
                .apply()
        }
    }

    fun selectImportedWaypoint(index: Int) {
        val waypoint = _importedWaypoints.value.getOrNull(index) ?: return
        _activeWaypointIndex.value = index
        _activeWaypoint.value = waypoint
        settings.edit()
            .putInt(PREF_ACTIVE_WAYPOINT_INDEX, index)
            .apply()
    }

    fun hideImportedWaypoint(index: Int) {
        val waypoint = _importedWaypoints.value.getOrNull(index) ?: return
        val hiddenKeys = hiddenWaypointKeys().toMutableSet()
        hiddenKeys += waypoint.hideKey()
        saveHiddenWaypointKeys(hiddenKeys)

        if (_activeWaypoint.value?.hideKey() == waypoint.hideKey()) {
            _activeWaypointIndex.value = NO_ACTIVE_WAYPOINT
            _activeWaypoint.value = null
            settings.edit().remove(PREF_ACTIVE_WAYPOINT_INDEX).apply()
        }

        refreshImportedWaypoints()
    }

    fun unhideAllWaypoints() {
        settings.edit().remove(PREF_HIDDEN_WAYPOINT_KEYS).apply()
        refreshImportedWaypoints()
    }

    fun cycleTrackingQualityMode() {
        val nextMode = _trackingQualityMode.value.next()
        _trackingQualityMode.value = nextMode
        settings.edit()
            .putString(PREF_TRACKING_MODE, nextMode.name)
            .apply()
    }

    fun recoverDraft() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val recovered = GpxWriter.promoteDraft(ctx)
            _hasDraft.value = GpxWriter.getDraftFile(ctx) != null
            if (recovered != null) {
                val rides = RideRepository.listRides(ctx)
                _savedRides.value = rides
                _screen.value = Screen.SAVED_RIDES
            }
        }
    }

    fun discardDraft() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            GpxWriter.disableDraftWrites()
            GpxWriter.clearDraft(ctx)
            _hasDraft.value = false
        }
    }

    fun startRide() {
        val ctx = getApplication<Application>()
        if (isTracking.value) {
            _screen.value = Screen.TRACKING
            return
        }
        _isMapZoomedIn.value = false
        _trackingPage.value = TrackingPage.MAP
        _activeWaypointIndex.value = NO_ACTIVE_WAYPOINT
        _activeWaypoint.value = null
        settings.edit()
            .putString(PREF_ACTIVE_TRACKING_PAGE, TrackingPage.MAP.name)
            .remove(PREF_ACTIVE_WAYPOINT_INDEX)
            .apply()
        ctx.startForegroundService(
            Intent(ctx, TrackingService::class.java).apply {
                putExtra(TrackingService.EXTRA_TRACKING_QUALITY_MODE, _trackingQualityMode.value.name)
            }
        )
        _screen.value = Screen.TRACKING
    }

    fun onMarkSpotTapped() {
        val pts = trackPoints.value
        if (pts.isEmpty()) return
        val last = pts.last()
        pendingWaypoint = PendingWaypoint(last.latitude, last.longitude, last.altitude)
        _screen.value = Screen.MARK_SPOT
    }

    fun confirmMarkSpot(name: String) {
        val pending = pendingWaypoint ?: return
        val waypoint = Waypoint(
            name = name.ifBlank { "Spot ${waypoints.value.size + 1}" },
            latitude = pending.lat,
            longitude = pending.lon,
            altitude = pending.alt,
            timestampMs = System.currentTimeMillis()
        )
        TrackingService.addWaypoint(waypoint)
        pendingWaypoint = null
        _screen.value = Screen.TRACKING
        saveDraftNow()
    }

    fun cancelMarkSpot() {
        pendingWaypoint = null
        _screen.value = Screen.TRACKING
    }

    fun onStopTapped() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_TRACKING
            }
        )
        _screen.value = Screen.SAVE_CONFIRM
    }

    fun onPauseResumeTapped() {
        val ctx = getApplication<Application>()
        val action = if (isPaused.value) {
            TrackingService.ACTION_RESUME_TRACKING
        } else {
            TrackingService.ACTION_PAUSE_TRACKING
        }
        ctx.startService(
            Intent(ctx, TrackingService::class.java).apply {
                this.action = action
            }
        )
    }

    fun promptToNameRide() {
        rideNamingMode = RideNamingMode.SAVE_NEW
        rideBeingRenamed = null
        _pendingRideName.value = defaultRideName()
        _screen.value = Screen.NAME_RIDE
    }

    fun promptToRenameRide(ride: RideSummary) {
        rideNamingMode = RideNamingMode.RENAME_EXISTING
        rideBeingRenamed = ride
        _pendingRideName.value = ride.name
        _screen.value = Screen.NAME_RIDE
    }

    fun confirmRideName(name: String) {
        val ctx = getApplication<Application>()
        val finalName = name.trim().ifBlank {
            _pendingRideName.value.ifBlank { defaultRideName() }
        }

        viewModelScope.launch {
            when (rideNamingMode) {
                RideNamingMode.SAVE_NEW -> {
                    withContext(Dispatchers.IO) {
                        GpxWriter.disableDraftWrites()
                        GpxWriter.write(ctx, trackPoints.value, waypoints.value, finalName)
                    }
                    _hasDraft.value = false
                    clearRideNamingState()
                    _isMapZoomedIn.value = false
                    clearActiveTrackingUiState()
                    TrackingService.reset()
                    _screen.value = Screen.HOME
                }

                RideNamingMode.RENAME_EXISTING -> {
                    val ride = rideBeingRenamed ?: return@launch
                    withContext(Dispatchers.IO) {
                        RideRepository.renameRide(ride, finalName)
                    }
                    refreshSavedRides()
                    clearRideNamingState()
                    _screen.value = Screen.SAVED_RIDES
                }
            }
        }
    }

    fun cancelRideNaming() {
        val returnScreen = if (rideNamingMode == RideNamingMode.SAVE_NEW) {
            Screen.SAVE_CONFIRM
        } else {
            Screen.SAVED_RIDES
        }
        clearRideNamingState()
        _screen.value = returnScreen
    }

    fun discardRide() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                GpxWriter.disableDraftWrites()
                GpxWriter.clearDraft(ctx)
            }
            _hasDraft.value = false
            clearRideNamingState()
            _isMapZoomedIn.value = false
            clearActiveTrackingUiState()
            TrackingService.reset()
            _screen.value = Screen.HOME
        }
    }

    fun navigateToSavedRides() {
        refreshSavedRides()
        _screen.value = Screen.SAVED_RIDES
    }

    fun navigateHome() {
        _selectedRideMap.value = null
        _screen.value = Screen.HOME
    }

    fun openSavedRideMap(ride: RideSummary) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { RideGpxParser.readRide(ride.file) }.getOrNull()
            } ?: return@launch

            _selectedRideMap.value = RideMapDetails(
                summary = ride,
                trackPoints = parsed.trackPoints,
                waypoints = parsed.waypoints
            )
            _screen.value = Screen.SAVED_RIDE_MAP
        }
    }

    fun closeSavedRideMap() {
        _selectedRideMap.value = null
        _screen.value = Screen.SAVED_RIDES
    }

    fun refreshSavedRides() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _isSavedRidesLoading.value = true
            val rides = try {
                withContext(Dispatchers.IO) {
                    RideRepository.listRides(ctx)
                }
            } finally {
                _isSavedRidesLoading.value = false
            }
            _savedRides.value = rides
        }
    }

    fun refreshImportedWaypoints() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val hiddenKeys = hiddenWaypointKeys()
            val allWaypoints = ImportedGpxRepository.listImportedWaypoints(ctx)
            val visibleWaypoints = allWaypoints.filterNot { it.hideKey() in hiddenKeys }
            _hiddenWaypointCount.value = allWaypoints.size - visibleWaypoints.size
            _importedWaypoints.value = visibleWaypoints
            restoreActiveWaypoint(visibleWaypoints)
        }
    }

    fun shareRide(ride: RideSummary): Intent {
        val ctx = getApplication<Application>()
        return RideRepository.buildShareIntent(ctx, ride)
    }

    suspend fun sendRideToPhone(ride: RideSummary): PhoneTransferResult {
        val ctx = getApplication<Application>()
        return PhoneTransferClient(ctx).sendRide(ride)
    }

    fun deleteRide(ride: RideSummary) {
        RideRepository.deleteRide(ride)
        if (_selectedRideMap.value?.summary?.file == ride.file) {
            _selectedRideMap.value = null
        }
        refreshSavedRides()
    }

    fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    fun formatDistance(meters: Double): String {
        val miles = meters / 1609.344
        return "%.2f mi".format(miles)
    }

    fun formatClockTime(timestampMs: Long): String {
        val context = getApplication<Application>()
        return DateFormat.getTimeFormat(context).format(Date(timestampMs))
    }

    private fun checkForDraft() {
        val ctx = getApplication<Application>()
        _hasDraft.value = GpxWriter.getDraftFile(ctx) != null
    }

    private fun saveDraftNow() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            GpxWriter.writeDraft(ctx, trackPoints.value, waypoints.value)
        }
    }

    private fun clearRideNamingState() {
        rideBeingRenamed = null
        _pendingRideName.value = ""
    }

    private fun restoreActiveWaypoint(imported: List<Waypoint>) {
        val savedIndex = settings.getInt(PREF_ACTIVE_WAYPOINT_INDEX, NO_ACTIVE_WAYPOINT)
        val index = when {
            savedIndex in imported.indices -> savedIndex
            _activeWaypointIndex.value in imported.indices -> _activeWaypointIndex.value
            else -> NO_ACTIVE_WAYPOINT
        }
        _activeWaypointIndex.value = index
        _activeWaypoint.value = imported.getOrNull(index)
    }

    private fun clearActiveTrackingUiState() {
        _trackingPage.value = TrackingPage.MAP
        _activeWaypointIndex.value = NO_ACTIVE_WAYPOINT
        _activeWaypoint.value = null
        settings.edit()
            .putString(PREF_ACTIVE_TRACKING_PAGE, TrackingPage.MAP.name)
            .remove(PREF_ACTIVE_WAYPOINT_INDEX)
            .apply()
    }

    private fun hiddenWaypointKeys(): Set<String> {
        return settings.getStringSet(PREF_HIDDEN_WAYPOINT_KEYS, emptySet()).orEmpty()
    }

    private fun saveHiddenWaypointKeys(keys: Set<String>) {
        settings.edit()
            .putStringSet(PREF_HIDDEN_WAYPOINT_KEYS, keys)
            .apply()
    }

    private fun Waypoint.hideKey(): String {
        return listOf(
            name.trim().lowercase(Locale.US),
            "%.6f".format(Locale.US, latitude),
            "%.6f".format(Locale.US, longitude)
        ).joinToString("|")
    }

    private fun defaultRideName(): String {
        return "Ride ${SimpleDateFormat("MMM d h:mm a", Locale.getDefault()).format(Date())}"
    }

    companion object {
        private const val PREFS_NAME = "tracker_settings"
        private const val PREF_TRACKING_MODE = "tracking_mode"
        private const val PREF_ACTIVE_TRACKING_PAGE = "active_tracking_page"
        private const val PREF_ACTIVE_WAYPOINT_INDEX = "active_waypoint_index"
        private const val PREF_HIDDEN_WAYPOINT_KEYS = "hidden_waypoint_keys"
        private const val NO_ACTIVE_WAYPOINT = -1
    }
}
