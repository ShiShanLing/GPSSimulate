package com.example.gpssimulate.ui

import android.Manifest
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gpssimulate.R
import com.example.gpssimulate.location.LocationHelper
import com.example.gpssimulate.location.MockLocationChecker
import com.example.gpssimulate.location.PresetLocation
import com.example.gpssimulate.location.PresetLocationParser
import com.example.gpssimulate.location.PresetLocationRepository
import com.example.gpssimulate.map.GeoCoordinateConverter
import com.example.gpssimulate.service.MockLocationService
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView

private const val DEFAULT_LATITUDE = 39.9042
private const val DEFAULT_LONGITUDE = 116.4074
private const val DEFAULT_MAP_ZOOM = 16f

private fun openDeveloperSettings(context: android.content.Context) {
    val intentActions = listOf(
        "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        Settings.ACTION_DEVICE_INFO_SETTINGS,
    )
    for (action in intentActions) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (_: Exception) {
            // Try next fallback action.
        }
    }
}

private fun moveMapToWgs84(mapView: MapView?, latitude: Double, longitude: Double) {
    val bd09 = GeoCoordinateConverter.wgs84ToBd09(latitude, longitude)
    mapView?.map?.animateMapStatus(
        MapStatusUpdateFactory.newLatLngZoom(bd09, DEFAULT_MAP_ZOOM)
    )
}

@Composable
fun LocationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var latitude by remember { mutableDoubleStateOf(DEFAULT_LATITUDE) }
    var longitude by remember { mutableDoubleStateOf(DEFAULT_LONGITUDE) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isMockApp by remember { mutableStateOf(MockLocationChecker.isMockLocationApp(context)) }
    var isMocking by remember { mutableStateOf(false) }
    var forceFreshLocateOnce by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf<String?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    val presetRepository = remember { PresetLocationRepository(context) }
    var presets by remember { mutableStateOf(presetRepository.getAll()) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    val isMockingHolder = remember { booleanArrayOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    fun locateMe() {
        if (isMocking) {
            locationStatus = "正在模拟定位中，请先停止模拟再获取真实位置"
            return
        }
        isLocating = true
        locationStatus = "正在定位..."
        LocationHelper.fetchCurrentLocation(
            context = context,
            forceFreshLocation = forceFreshLocateOnce,
            onSuccess = { lat, lng ->
                isLocating = false
                locationStatus = null
                forceFreshLocateOnce = false
                latitude = lat
                longitude = lng
                mapView?.let { moveMapToWgs84(it, lat, lng) }
            },
            onFailure = { message ->
                isLocating = false
                locationStatus = message
            }
        )
    }

    fun applyPreset(preset: PresetLocation) {
        locationStatus = null
        latitude = preset.latitude
        longitude = preset.longitude
        mapView?.let { moveMapToWgs84(it, preset.latitude, preset.longitude) }
        if (isMocking) {
            MockLocationService.update(context, preset.latitude, preset.longitude)
        }
    }

    LaunchedEffect(isMocking) {
        isMockingHolder[0] = isMocking
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission && mapView != null) {
            locateMe()
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = LocationHelper.hasLocationPermission(context)
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(mapView, hasLocationPermission) {
        if (mapView != null && hasLocationPermission) {
            locateMe()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isMockApp = MockLocationChecker.isMockLocationApp(context)
                    mapView?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            map.isMyLocationEnabled = false
                            val initialBd09 = GeoCoordinateConverter.wgs84ToBd09(latitude, longitude)
                            map.setMapStatus(
                                MapStatusUpdateFactory.newLatLngZoom(initialBd09, DEFAULT_MAP_ZOOM)
                            )

                            val scrollHandler = Handler(Looper.getMainLooper())
                            var scrollRunnable: Runnable? = null

                            map.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
                                override fun onMapStatusChangeStart(status: MapStatus?) = Unit

                                override fun onMapStatusChangeStart(status: MapStatus?, reason: Int) = Unit

                                override fun onMapStatusChange(status: MapStatus?) = Unit

                                override fun onMapStatusChangeFinish(status: MapStatus?) {
                                    scrollRunnable?.let(scrollHandler::removeCallbacks)
                                    scrollRunnable = Runnable {
                                        val target = status?.target ?: return@Runnable
                                        val (wgsLat, wgsLng) = GeoCoordinateConverter.bd09ToWgs84(
                                            target.latitude,
                                            target.longitude,
                                        )
                                        latitude = wgsLat
                                        longitude = wgsLng
                                        if (isMockingHolder[0]) {
                                            MockLocationService.update(
                                                context,
                                                latitude,
                                                longitude,
                                            )
                                        }
                                    }
                                    scrollHandler.postDelayed(scrollRunnable!!, 300)
                                }
                            })

                            mapView = this
                        }
                    },
                    update = { view ->
                        mapView = view
                    }
                )

                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    FilledIconButton(onClick = { presetMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = stringResource(R.string.preset_locations)
                        )
                    }
                    PresetLocationDropdown(
                        expanded = presetMenuExpanded,
                        presets = presets,
                        onDismiss = { presetMenuExpanded = false },
                        onSelect = { preset ->
                            presetMenuExpanded = false
                            applyPreset(preset)
                        },
                        onAddClick = {
                            presetMenuExpanded = false
                            showAddPresetDialog = true
                        },
                    )
                }
            }

            if (showAddPresetDialog) {
                AddPresetDialog(
                    onDismiss = { showAddPresetDialog = false },
                    onConfirm = { input ->
                        PresetLocationParser.parse(input).fold(
                            onSuccess = { preset ->
                                presetRepository.add(preset)
                                presets = presetRepository.getAll()
                                showAddPresetDialog = false
                                applyPreset(preset)
                                null
                            },
                            onFailure = { it.message },
                        )
                    },
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "拖动地图，将图钉对准目标位置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    locationStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { locateMe() },
                            modifier = Modifier.weight(1f),
                            enabled = hasLocationPermission && !isLocating
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                            Text("当前位置", modifier = Modifier.padding(start = 4.dp))
                        }

                        if (isMocking) {
                            Button(
                                onClick = {
                                    MockLocationService.stop(context)
                                    isMocking = false
                                    forceFreshLocateOnce = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Text("停止模拟", modifier = Modifier.padding(start = 4.dp))
                            }
                        } else {
                            Button(
                                onClick = {
                                    try {
                                        MockLocationService.start(context, latitude, longitude)
                                        isMocking = true
                                        locationStatus = null
                                    } catch (_: SecurityException) {
                                        isMocking = false
                                        isMockApp = MockLocationChecker.isMockLocationApp(context)
                                        locationStatus = "请先在开发者选项中将本应用设为模拟定位应用"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isMockApp
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                                Text("开始模拟", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }

                    if (isMocking) {
                        Text(
                            text = stringResource(R.string.mock_tip_disable_gps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        if (!isMockApp) {
            MockLocationSetupDialog(
                onOpenSettings = {
                    openDeveloperSettings(context)
                },
                onRefresh = {
                    isMockApp = MockLocationChecker.isMockLocationApp(context)
                },
            )
        }
    }
}

@Composable
private fun MockLocationSetupDialog(
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(text = stringResource(R.string.mock_setup_dialog_title))
        },
        text = {
            Text(text = stringResource(R.string.mock_setup_dialog_message))
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.mock_setup_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onRefresh) {
                Text(text = stringResource(R.string.mock_setup_dialog_retry))
            }
        },
    )
}

@Composable
private fun PresetLocationDropdown(
    expanded: Boolean,
    presets: List<PresetLocation>,
    onDismiss: () -> Unit,
    onSelect: (PresetLocation) -> Unit,
    onAddClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        presets.forEach { preset ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(
                            R.string.preset_location_item,
                            preset.name,
                            preset.latitude,
                            preset.longitude,
                        )
                    )
                },
                onClick = { onSelect(preset) },
            )
        }
        DropdownMenuItem(
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.preset_add_entry))
                }
            },
            onClick = onAddClick,
        )
    }
}

@Composable
private fun AddPresetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.preset_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.preset_add_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.preset_add_dialog_hint)) },
                    singleLine = true,
                    isError = errorMessage != null,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = onConfirm(input)
            }) {
                Text(text = stringResource(R.string.preset_add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.preset_add_dialog_cancel))
            }
        },
    )
}
