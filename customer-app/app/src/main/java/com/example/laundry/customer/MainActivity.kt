package com.example.laundry.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.laundry.core.api.OrderItemRequest
import com.example.laundry.core.api.RetrofitClient
import com.example.laundry.core.model.*
import com.example.laundry.core.repository.LaundryRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val dashboardGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF4F7FB),
        Color(0xFFF8FAFC),
        Color(0xFFEFF6FF)
    )
)

private val cardGlow = Brush.linearGradient(
    listOf(
        Color(0xFFDBF7F0),
        Color(0xFFE8F1FF),
        Color(0xFFEDEBFF)
    )
)

private val accentSurface = Color(0xFFFFFFFF)

@Composable
private fun LaundryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F172A),
            secondary = Color(0xFF2563EB),
            tertiary = Color(0xFF14B8A6),
            background = Color(0xFFF4F7FB),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            outline = Color(0xFFCBD5E1)
        ),
        content = content
    )
}

enum class CustomerPaymentMethod {
    WALLET,
    CARD,
    CASH_ON_DELIVERY
}

enum class AuthRole {
    CUSTOMER,
    VENDOR,
    DRIVER
}

class MainActivity : ComponentActivity() {
    private val repository = LaundryRepository(RetrofitClient.apiService)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaundryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedRole by remember { mutableStateOf(AuthRole.CUSTOMER) }
                    var isLoggedIn by remember { mutableStateOf(false) }

                    if (!isLoggedIn) {
                        val scope = rememberCoroutineScope()
                        RoleLoginScreen(
                            selectedRole = selectedRole,
                            onRoleChange = { selectedRole = it },
                            onLogin = { email, password ->
                                scope.launch {
                                    val success = repository.loginUser(selectedRole.name.lowercase(), email, password)
                                    if (success != null) {
                                        isLoggedIn = true
                                    }
                                }
                            },
                            onRegister = { name, phone, email, password ->
                                scope.launch {
                                    repository.registerUser(name, email, password, selectedRole.name.lowercase(), phone)
                                }
                            },
                            onForgotPassword = { email ->
                                scope.launch {
                                    repository.forgotPassword(email, selectedRole.name.lowercase())
                                }
                            }
                        )
                    } else {
                        MainScreen(
                            repository = repository,
                            onLogout = { isLoggedIn = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
    )
}

@Composable
private fun PickupOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

private enum class AuthMode {
    LOGIN,
    SIGN_UP,
    FORGOT_PASSWORD
}

@Composable
private fun RoleLoginScreen(
    selectedRole: AuthRole,
    onRoleChange: (AuthRole) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (name: String, phone: String, email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var authMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dashboardGradient)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFFFFFFF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.laundry_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Laundry Market",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (authMode) {
                        AuthMode.LOGIN -> "Sign in to continue"
                        AuthMode.SIGN_UP -> "Create your account"
                        AuthMode.FORGOT_PASSWORD -> "Reset your password"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoleSelectorChip(
                        label = "Customer",
                        selected = selectedRole == AuthRole.CUSTOMER,
                        onClick = { onRoleChange(AuthRole.CUSTOMER) },
                        modifier = Modifier.weight(1f)
                    )
                    RoleSelectorChip(
                        label = "Vendor",
                        selected = selectedRole == AuthRole.VENDOR,
                        onClick = { onRoleChange(AuthRole.VENDOR) },
                        modifier = Modifier.weight(1f)
                    )
                    RoleSelectorChip(
                        label = "Driver",
                        selected = selectedRole == AuthRole.DRIVER,
                        onClick = { onRoleChange(AuthRole.DRIVER) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (authMode) {
                    AuthMode.LOGIN -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email or phone") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { onLogin(email, password) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = email.isNotBlank() && password.isNotBlank()
                        ) {
                            Text("Login as ${selectedRole.name.lowercase().replaceFirstChar { it.titlecase() }}")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { authMode = AuthMode.SIGN_UP }) {
                                Text("Sign up")
                            }
                            TextButton(onClick = { authMode = AuthMode.FORGOT_PASSWORD }) {
                                Text("Forgot password?")
                            }
                        }
                    }
                    AuthMode.SIGN_UP -> {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone number") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Create password") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                onRegister(fullName, phoneNumber, email, password)
                                authMessage = "Account created for $fullName. Please sign in to continue."
                                authMode = AuthMode.LOGIN
                                fullName = ""
                                phoneNumber = ""
                                password = ""
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fullName.isNotBlank() && phoneNumber.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                        ) {
                            Text("Create account")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { authMode = AuthMode.LOGIN }) {
                            Text("Back to sign in")
                        }
                    }
                    AuthMode.FORGOT_PASSWORD -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email or phone") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                onForgotPassword(email)
                                authMessage = "Password reset instructions have been sent to $email."
                                authMode = AuthMode.LOGIN
                                email = ""
                                password = ""
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = email.isNotBlank()
                        ) {
                            Text("Send reset link")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { authMode = AuthMode.LOGIN }) {
                            Text("Back to sign in")
                        }
                    }
                }

                if (authMessage != null && authMode == AuthMode.LOGIN) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = authMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0F766E)
                    )
                }

                if (authMode == AuthMode.LOGIN) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Demo access: any email and password will work in this prototype",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: LaundryRepository, onLogout: () -> Unit = {}) {
    var currentTab by remember { mutableStateOf(0) }
    var selectedShop by remember { mutableStateOf<Shop?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (selectedShop == null) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.laundry_logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentTab == 0) "Laundry Market" else "My Orders",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onLogout) {
                            Text("Logout")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        bottomBar = {
            if (selectedShop == null) {
                NavigationBar(
                    containerColor = Color(0xFFFFFFFF),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, "Shops") },
                        label = { Text("Shops") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.List, "Orders") },
                        label = { Text("Orders") }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dashboardGradient)
                .padding(padding)
        ) {
            if (selectedShop != null) {
                BookingScreen(selectedShop!!, repository, onBack = { selectedShop = null })
            } else {
                if (currentTab == 0) {
                    ShopBrowser(repository, onShopSelected = { selectedShop = it })
                } else {
                    CustomerDashboard(repository, onBookNearbyShop = { selectedShop = it })
                }
            }
        }
    }
}

@Composable
fun ShopBrowser(repository: LaundryRepository, onShopSelected: (Shop) -> Unit) {
    var shops by remember { mutableStateOf(emptyList<Shop>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location -> userLocation = location }
        }
    }

    LaunchedEffect(Unit) {
        shops = repository.getShops()
        val hasFinePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFinePermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location -> userLocation = location }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val shopsWithDistance = remember(shops, userLocation) {
        shops.map { shop ->
            val distanceKm = userLocation?.let {
                distanceBetweenKm(it.latitude, it.longitude, shop.latitude, shop.longitude)
            }
            shop to distanceKm
        }
    }

    val filteredShops = remember(shopsWithDistance, searchQuery, selectedFilter) {
        shopsWithDistance.filter { (shop, _) ->
            val matchesQuery = shop.name.contains(searchQuery, ignoreCase = true) ||
                shop.address.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "Popular" -> shop.rating >= 4.5f
                "Fast" -> true
                "Eco" -> true
                else -> true
            }
            matchesQuery && matchesFilter
        }.sortedBy { (_, distanceKm) -> distanceKm ?: Double.MAX_VALUE }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardGlow)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Healthy flow, clear pricing",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your neighborhood laundry essentials, delivered with care.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search shops or areas") },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Popular", "Fast", "Eco").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredShops.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFFFFF)
            ) {
                Text(
                    text = "No shops match your search.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredShops) { (shop, distanceKm) ->
                    ShopItem(shop, distanceKm) { onShopSelected(shop) }
                }
            }
        }
    }
}

@Composable
fun ShopItem(shop: Shop, distanceKm: Double?, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accentSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val locationText = if (distanceKm == null) shop.address else "${shop.address} • ${String.format("%.1f", distanceKm)} km away"
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEAF3FF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(shop.rating.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (distanceKm != null && distanceKm < 5.0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nearby",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { openDirections(context, shop) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Directions")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(shop: Shop, repository: LaundryRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedPaymentMethod by remember { mutableStateOf(CustomerPaymentMethod.WALLET) }
    var showPaymentMethodDialog by remember { mutableStateOf(false) }
    var pickupWindow by remember { mutableStateOf("ASAP") }
    val services = if (shop.services.isEmpty()) {
        listOf(
            LaundryService("1", "Wash & Fold", 500, "kg"),
            LaundryService("2", "Dry Cleaning", 1200, "piece"),
            LaundryService("3", "Ironing", 300, "piece")
        )
    } else shop.services

    val pickupOptions = listOf(
        "ASAP" to "As soon as possible",
        "Today 10:00-12:00" to "Today • 10:00 am - 12:00 pm",
        "Today 12:00-14:00" to "Today • 12:00 pm - 2:00 pm",
        "Tomorrow 09:00-11:00" to "Tomorrow • 9:00 am - 11:00 am"
    )

    var selectedQuantities by remember { mutableStateOf(services.associate { it.id to 0 }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dashboardGradient)
    ) {
        TopAppBar(
            title = { Text(shop.name, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Book a service", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select the care plan you need today.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Surface(
                    color = Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Pickup window",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PickupOptionRow(
                            title = "ASAP",
                            subtitle = "As soon as possible",
                            selected = pickupWindow == "ASAP",
                            onClick = { pickupWindow = "ASAP" }
                        )
                        PickupOptionRow(
                            title = "Today 10:00-12:00",
                            subtitle = "Today • 10:00 am - 12:00 pm",
                            selected = pickupWindow == "Today 10:00-12:00",
                            onClick = { pickupWindow = "Today 10:00-12:00" }
                        )
                        PickupOptionRow(
                            title = "Today 12:00-14:00",
                            subtitle = "Today • 12:00 pm - 2:00 pm",
                            selected = pickupWindow == "Today 12:00-14:00",
                            onClick = { pickupWindow = "Today 12:00-14:00" }
                        )
                        PickupOptionRow(
                            title = "Tomorrow 09:00-11:00",
                            subtitle = "Tomorrow • 9:00 am - 11:00 am",
                            selected = pickupWindow == "Tomorrow 09:00-11:00",
                            onClick = { pickupWindow = "Tomorrow 09:00-11:00" }
                        )
                    }
                }
            }

            item {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Pickup location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = shop.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { openDirections(context, shop) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open map")
                        }
                    }
                }
            }

            items(services) { service ->
                val quantity = selectedQuantities[service.id] ?: 0
                ServiceItem(service, quantity) { newQty ->
                    selectedQuantities = selectedQuantities.toMutableMap().apply { put(service.id, newQty) }
                }
            }
        }

        val totalCents = services.sumOf { (selectedQuantities[it.id] ?: 0) * it.priceCents }

        Surface(
            color = Color(0xFFFFFFFF),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₦${String.format("%.2f", totalCents / 100.0)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pickup: ${pickupOptions.firstOrNull { it.first == pickupWindow }?.second ?: "As soon as possible"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val items = services.filter { (selectedQuantities[it.id] ?: 0) > 0 }.map {
                                    OrderItemRequest(it.name, selectedQuantities[it.id] ?: 0)
                                }
                                if (items.isNotEmpty()) {
                                    repository.createOrder(shop.id, items)
                                    onBack()
                                }
                            }
                        },
                        enabled = totalCents > 0,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Book now")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { openDirections(context, shop) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Get directions")
                    }
                    OutlinedButton(
                        onClick = { showPaymentMethodDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Payment: ${paymentMethodLabel(selectedPaymentMethod)}")
                    }
                }
            }
        }
    }

    if (showPaymentMethodDialog) {
        PaymentMethodDialog(
            selected = selectedPaymentMethod,
            onDismiss = { showPaymentMethodDialog = false },
            onSelect = {
                selectedPaymentMethod = it
                showPaymentMethodDialog = false
            }
        )
    }
}

@Composable
fun ServiceItem(service: LaundryService, quantity: Int, onQuantityChange: (Int) -> Unit) {
    Surface(
        color = Color(0xFFFFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("₦${String.format("%.2f", service.priceCents / 100.0)} / ${service.unit}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2563EB))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { if (quantity > 0) onQuantityChange(quantity - 1) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("-")
                }
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { onQuantityChange(quantity + 1) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
fun CustomerDashboard(repository: LaundryRepository, onBookNearbyShop: (Shop) -> Unit = {}) {
    val orders by repository.getCustomerOrders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var nearbyShop by remember { mutableStateOf<Shop?>(null) }
    var nearbyDistance by remember { mutableStateOf<Double?>(null) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showKycDialog by remember { mutableStateOf(false) }
    var showVendorContactDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf(CustomerPaymentMethod.WALLET) }
    var kycVerified by remember { mutableStateOf(false) }
    var additionalWalletBalance by remember { mutableStateOf(0.0) }
    var topUpStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val shops = repository.getShops()
        val location = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                suspendCancellableCoroutine<Location?> { continuation ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location -> continuation.resume(location) }
                        .addOnFailureListener { continuation.resume(null) }
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        userLocation = location
        nearbyShop = shops.minByOrNull { shop ->
            if (location == null) return@minByOrNull Double.MAX_VALUE
            distanceBetweenKm(location.latitude, location.longitude, shop.latitude, shop.longitude)
        }
        nearbyDistance = nearbyShop?.let { shop ->
            if (location == null) null else distanceBetweenKm(location.latitude, location.longitude, shop.latitude, shop.longitude)
        }
    }

    val walletBalance = orders.sumOf { it.totalCents } / 100.0 + additionalWalletBalance
    val monthlySpend = orders.filter { it.status == OrderStatus.DELIVERED }.sumOf { it.totalCents } / 100.0
    val activeOrders = orders.count { it.status != OrderStatus.DELIVERED && it.status != OrderStatus.CANCELLED }
    val deliveredCount = orders.count { it.status == OrderStatus.DELIVERED }
    val inTransitCount = orders.count { it.status in listOf(OrderStatus.PICKUP_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.AT_VENDOR, OrderStatus.PROCESSING, OrderStatus.READY_FOR_DELIVERY, OrderStatus.DELIVERY_ASSIGNED, OrderStatus.OUT_FOR_DELIVERY) }
    val trackingOrder = orders.firstOrNull { it.status != OrderStatus.DELIVERED && it.status != OrderStatus.CANCELLED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        KycStatusCard(
            title = "Customer KYC",
            subtitle = if (kycVerified) "Verified and ready to order" else "Complete identity check to unlock services",
            verified = kycVerified,
            onActionClick = { showKycDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        WalletSummaryCard(
            balance = walletBalance,
            monthlySpend = monthlySpend,
            activeOrders = activeOrders
        )

        Spacer(modifier = Modifier.height(16.dp))

        NearbyStoreCard(
            shop = nearbyShop,
            distanceKm = nearbyDistance,
            onOpenDirections = { shop -> openDirections(context, shop) },
            onBookNow = onBookNearbyShop
        )

        Spacer(modifier = Modifier.height(16.dp))

        PaymentMethodCard(
            selected = selectedPaymentMethod,
            onChange = { showPaymentDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill("Orders", orders.size.toString(), Modifier.weight(1f))
            StatPill("Active", activeOrders.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFFFFF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Order summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryMetricCard("Delivered", deliveredCount.toString(), Modifier.weight(1f))
                    SummaryMetricCard("In transit", inTransitCount.toString(), Modifier.weight(1f))
                    SummaryMetricCard("Total", orders.size.toString(), Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (trackingOrder != null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFE0F2FE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Live tracking",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = trackingStatusLabel(trackingOrder.status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { getStatusProgress(trackingOrder.status) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = Color(0xFFBFDBFE),
                        color = Color(0xFF2563EB)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Order #${trackingOrder.id.takeLast(4)} • ${trackingOrder.status.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionButton("Top up", onClick = { showTopUpDialog = true })
            QuickActionButton("History", onClick = { showHistoryDialog = true })
            QuickActionButton("Payment", onClick = { showPaymentDialog = true })
            QuickActionButton("Contact vendor", onClick = { showVendorContactDialog = true })
        }

        if (topUpStatus.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE0F2FE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = topUpStatus,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFE0F2FE),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "If app notifications are delayed, call or message the vendor directly for live order updates.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(orders) { order ->
                CustomerOrderListItem(
                    order,
                    onRate = { rating, comment ->
                        scope.launch { repository.submitReview(order.id, rating, comment) }
                    },
                    onDispute = { reason ->
                        scope.launch { repository.openDispute(order.id, reason) }
                    }
                )
            }
        }
    }

    if (showTopUpDialog) {
        WalletTopUpDialog(
            onDismiss = { showTopUpDialog = false },
            onConfirm = { amount ->
                additionalWalletBalance += amount
                topUpStatus = "Wallet topped up with ₦${String.format("%.2f", amount)}"
                showTopUpDialog = false
            }
        )
    }

    if (showPaymentDialog) {
        PaymentMethodDialog(
            selected = selectedPaymentMethod,
            onDismiss = { showPaymentDialog = false },
            onSelect = {
                selectedPaymentMethod = it
                showPaymentDialog = false
            }
        )
    }

    if (showVendorContactDialog) {
        VendorContactDialog(
            vendorId = orders.firstOrNull { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.DELIVERED }?.vendorId ?: "vendor-default",
            onDismiss = { showVendorContactDialog = false }
        )
    }

    if (showHistoryDialog) {
        OrderHistoryDialog(
            orders = orders,
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showKycDialog) {
        KycVerificationDialog(
            role = "Customer",
            onDismiss = { showKycDialog = false },
            onComplete = {
                kycVerified = true
                showKycDialog = false
            }
        )
    }
}

@Composable
private fun OrderHistoryDialog(orders: List<Order>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent order history") },
        text = {
            if (orders.isEmpty()) {
                Text("You have no orders yet. Start with a nearby shop and place your first booking.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    orders.take(5).forEach { order ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Order #${order.id.takeLast(4)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${order.status.name} • ₦${String.format("%.2f", order.totalCents / 100.0)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun openDirections(context: android.content.Context, shop: Shop) {
    val uri = if (shop.latitude != 0.0 || shop.longitude != 0.0) {
        Uri.parse("geo:${shop.latitude},${shop.longitude}?q=${shop.latitude},${shop.longitude}(${shop.name})")
    } else {
        Uri.parse("https://www.google.com/maps/search/?api=1&query=${shop.address.replace(" ", "+")}")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun paymentMethodLabel(method: CustomerPaymentMethod): String = when (method) {
    CustomerPaymentMethod.WALLET -> "Wallet"
    CustomerPaymentMethod.CARD -> "Card"
    CustomerPaymentMethod.CASH_ON_DELIVERY -> "Cash"
}

@Composable
private fun PaymentMethodCard(selected: CustomerPaymentMethod, onChange: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Payment method", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                Spacer(modifier = Modifier.height(4.dp))
                Text(paymentMethodLabel(selected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onChange, shape = RoundedCornerShape(14.dp)) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun PaymentMethodDialog(
    selected: CustomerPaymentMethod,
    onDismiss: () -> Unit,
    onSelect: (CustomerPaymentMethod) -> Unit
) {
    val methods = listOf(
        CustomerPaymentMethod.WALLET to "Wallet",
        CustomerPaymentMethod.CARD to "Debit / Credit Card",
        CustomerPaymentMethod.CASH_ON_DELIVERY to "Cash on delivery"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose payment method") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { (method, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label)
                        RadioButton(
                            selected = selected == method,
                            onClick = { onSelect(method) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun KycStatusCard(title: String, subtitle: String, verified: Boolean, onActionClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (verified) Color(0xFFE0F2FE) else Color(0xFFFFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (verified) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (verified) "Verified" else "Verify")
            }
        }
    }
}

@Composable
private fun KycVerificationDialog(role: String, onDismiss: () -> Unit, onComplete: () -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$role KYC verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Complete the checks below to pass verification.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("ID / Passport number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                enabled = fullName.isNotBlank() && phone.isNotBlank() && idNumber.isNotBlank()
            ) {
                Text("Pass KYC")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun VendorContactDialog(vendorId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vendorPhone = remember(vendorId) { vendorPhoneNumber(vendorId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contact the vendor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Notifications can be delayed. Reach the shop directly for real-time order updates.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Phone: $vendorPhone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$vendorPhone"))
                    context.startActivity(intent)
                    onDismiss()
                }) {
                    Text("Call")
                }
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$vendorPhone")).apply {
                        putExtra("sms_body", "Hi, I’m checking on my laundry order. Please confirm the current status.")
                    }
                    context.startActivity(intent)
                    onDismiss()
                }) {
                    Text("Message")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun vendorPhoneNumber(vendorId: String): String {
    val sanitized = vendorId.filter { it.isDigit() || it.isLetter() }
    val seed = if (sanitized.isEmpty()) "vendor" else sanitized
    val number = kotlin.math.abs(seed.hashCode()) % 900000 + 100000
    return "+1 555 ${String.format("%06d", number).take(3)} ${String.format("%06d", number).drop(3)}"
}

@Composable
private fun WalletTopUpDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var customAmountText by remember { mutableStateOf("") }
    val quickAmounts = listOf(250.0, 500.0, 1000.0, 2000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Top up wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add funds for your next order. Choose a quick amount or enter a custom value.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = customAmountText,
                    onValueChange = { customAmountText = it },
                    label = { Text("Custom amount (NGN)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickAmounts.forEach { amount ->
                        OutlinedButton(
                            onClick = { customAmountText = amount.toString() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("₦${String.format("%.0f", amount)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            val finalAmount = customAmountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (finalAmount > 0.0) {
                        onConfirm(finalAmount)
                    }
                },
                enabled = finalAmount > 0.0
            ) {
                Text("Add funds")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NearbyStoreCard(shop: Shop?, distanceKm: Double?, onOpenDirections: (Shop) -> Unit, onBookNow: (Shop) -> Unit) {
    if (shop == null) return

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby store",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (distanceKm == null) "Tap to view" else "${String.format("%.1f", distanceKm)} km",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = shop.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = shop.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenDirections(shop) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Directions")
                }
                OutlinedButton(onClick = { onBookNow(shop) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Book now")
                }
            }
        }
    }
}

@Composable
private fun WalletSummaryCard(balance: Double, monthlySpend: Double, activeOrders: Int) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Wallet", style = MaterialTheme.typography.labelLarge, color = Color(0xFF93C5FD))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₦${String.format("%.2f", balance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color(0xFF1D4ED8)
                ) {
                    Text(
                        text = "$activeOrders active",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricCard("This month", "₦${String.format("%.2f", monthlySpend)}", Modifier.weight(1f))
                SummaryMetricCard("Saved", "₦${String.format("%.2f", maxOf(0.0, balance - monthlySpend))}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E293B)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFDBFE))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun QuickActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEAF3FF)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2563EB))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CustomerOrderListItem(order: Order, onRate: (Int, String) -> Unit, onDispute: (String) -> Unit) {
    var showRatingDialog by remember { mutableStateOf(false) }
    var showDisputeDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.id.takeLast(4)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = order.status.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = trackingStatusLabel(order.status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            LinearProgressIndicator(
                progress = { getStatusProgress(order.status) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = Color(0xFF26415A),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (order.status == OrderStatus.DELIVERED) {
                    Button(onClick = { showRatingDialog = true }, shape = RoundedCornerShape(14.dp)) {
                        Text("Rate")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (order.status != OrderStatus.CANCELLED && order.status != OrderStatus.REQUESTED) {
                    OutlinedButton(
                        onClick = { showDisputeDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Text("Dispute", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                VendorContactButton(vendorId = order.vendorId ?: "vendor-default")
            }
        }
    }

    if (showRatingDialog) {
        RatingDialog(
            onDismiss = { showRatingDialog = false },
            onSubmit = { rating, comment ->
                onRate(rating, comment)
                showRatingDialog = false
            }
        )
    }

    if (showDisputeDialog) {
        DisputeDialog(
            onDismiss = { showDisputeDialog = false },
            onSubmit = { reason ->
                onDispute(reason)
                showDisputeDialog = false
            }
        )
    }
}

@Composable
private fun VendorContactButton(vendorId: String) {
    val context = LocalContext.current
    val vendorPhone = remember(vendorId) { vendorPhoneNumber(vendorId) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$vendorPhone"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
        ) {
            Text("Call vendor")
        }
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$vendorPhone")).apply {
                    putExtra("sms_body", "Hi, I’m checking on my laundry order. Please update me on the current status.")
                }
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Message")
        }
    }
}

@Composable
fun DisputeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a Dispute") },
        text = {
            Column {
                Text("Please explain the issue with your order.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Dispute") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RatingDialog(onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate your Experience") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1..5).forEach { index ->
                        IconButton(onClick = { rating = index }) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index <= rating) Color(0xFFFFD166) else Color(0xFF5A6B7A)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(rating, comment) }) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun getStatusProgress(status: OrderStatus?): Float {
    return when (status) {
        OrderStatus.REQUESTED, OrderStatus.QUEUED -> 0.1f
        OrderStatus.ACCEPTED -> 0.2f
        OrderStatus.PICKUP_ASSIGNED -> 0.3f
        OrderStatus.PICKED_UP -> 0.4f
        OrderStatus.AT_VENDOR -> 0.5f
        OrderStatus.PROCESSING -> 0.7f
        OrderStatus.READY_FOR_DELIVERY -> 0.8f
        OrderStatus.DELIVERY_ASSIGNED -> 0.9f
        OrderStatus.OUT_FOR_DELIVERY -> 0.95f
        OrderStatus.DELIVERED -> 1.0f
        else -> 0.0f
    }
}
