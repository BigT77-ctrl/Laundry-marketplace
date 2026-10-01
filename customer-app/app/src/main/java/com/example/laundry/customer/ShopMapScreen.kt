package com.example.laundry.customer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.laundry.core.model.Shop
import com.example.laundry.core.repository.LaundryRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopMapScreen(repository: LaundryRepository, onShopSelected: (Shop) -> Unit) {
    val context = LocalContext.current
    val isMapsApiKeyConfigured = remember(context) {
        context.getString(R.string.google_maps_key).isNotBlank()
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }
    val scope = rememberCoroutineScope()
    var shops by remember { mutableStateOf(emptyList<Shop>()) }
    var selectedShop by remember { mutableStateOf<Shop?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var locationRequestVersion by remember { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = context.hasLocationPermission()
        if (!hasLocationPermission) {
            locationMessage = "Location permission is off. You can still browse shops and directions."
        } else if (permissions.values.any { it }) {
            locationMessage = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            shops = repository.getShops()
        } catch (_: Exception) {
            loadError = true
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(hasLocationPermission, locationRequestVersion) {
        if (!hasLocationPermission) return@LaunchedEffect

        val locationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            locationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = LatLng(location.latitude, location.longitude)
                        locationMessage = null
                    } else {
                        locationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            CancellationTokenSource().token
                        ).addOnSuccessListener { currentLocation ->
                            if (currentLocation != null) {
                                userLocation = LatLng(currentLocation.latitude, currentLocation.longitude)
                                locationMessage = null
                            } else {
                                locationMessage = "Couldn't find your location. Showing available shop locations."
                            }
                        }.addOnFailureListener {
                            locationMessage = "Couldn't find your location. Showing available shop locations."
                        }
                    }
                }
                .addOnFailureListener {
                    locationMessage = "Couldn't find your location. Showing available shop locations."
                }
        } catch (_: SecurityException) {
            hasLocationPermission = false
        }
    }

    val fallbackShopLocation = shops.firstNotNullOfOrNull { it.mapPosition() }
    LaunchedEffect(userLocation, fallbackShopLocation) {
        val center = userLocation ?: fallbackShopLocation
        if (center != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(center, if (userLocation != null) 14f else 12f)
            )
        }
    }

    LaunchedEffect(selectedShop) {
        selectedShop?.mapPosition()?.let { position ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(position, 14f))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Find a laundry shop",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Nearby shops and your current location",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(0.58f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
            ) {
                shops.forEach { shop ->
                    shop.mapPosition()?.let { position ->
                        key(shop.id) {
                            Marker(
                                state = MarkerState(position = position),
                                title = shop.name,
                                snippet = shop.address,
                                onClick = {
                                    selectedShop = shop
                                    true
                                }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    if (context.hasLocationPermission()) {
                        hasLocationPermission = true
                        userLocation?.let { currentLocation ->
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(currentLocation, 14f)
                                )
                            }
                        } ?: run {
                            locationRequestVersion += 1
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Center on my location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (shops.none { it.mapPosition() != null }) {
                Text(
                    "Shop pins appear when a shop has map coordinates",
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isMapsApiKeyConfigured) {
                Text(
                    "Add MAPS_API_KEY to customer-app/local.properties",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            locationMessage?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Available shops", style = MaterialTheme.typography.titleLarge)
            if (shops.any { it.mapPosition() == null }) {
                Text(
                    "Some locations unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().weight(0.42f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            loadError -> Text(
                "Couldn't load shops. Please try again later.",
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.error
            )
            shops.isEmpty() -> Text(
                "No shops are available right now.",
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(modifier = Modifier.weight(0.42f)) {
                items(shops, key = { it.id }) { shop ->
                    ShopItem(shop) { selectedShop = shop }
                }
            }
        }
    }

    selectedShop?.let { shop ->
        ModalBottomSheet(
            onDismissRequest = { selectedShop = null },
            sheetState = sheetState
        ) {
            ShopDetailsSheet(
                shop = shop,
                onDirections = { openDirections(context, shop) },
                onBook = { onShopSelected(shop) }
            )
        }
    }
}

@Composable
private fun ShopDetailsSheet(shop: Shop, onDirections: () -> Unit, onBook: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(shop.name, style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                shop.address,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Rating",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                " ${shop.rating} rating",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (shop.mapPosition() == null) {
            Text(
                "This shop doesn't have map coordinates yet. Directions will search for its address.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onDirections, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Directions, contentDescription = null)
                Text("Directions", modifier = Modifier.padding(start = 6.dp))
            }
            Button(onClick = onBook, modifier = Modifier.weight(1f)) {
                Text("View services")
            }
        }
    }
}

private fun Shop.mapPosition(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
        return null
    }
    return LatLng(lat, lng)
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun openDirections(context: Context, shop: Shop) {
    val position = shop.mapPosition()
    val query = position?.let { "${it.latitude},${it.longitude}" } ?: shop.address
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
                )
            )
        } catch (_: ActivityNotFoundException) {
            Unit
        }
    }
}
