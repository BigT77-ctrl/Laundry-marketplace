package com.example.laundry.vendor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.laundry.core.api.RetrofitClient
import com.example.laundry.core.model.LaundryService
import com.example.laundry.core.model.Order
import com.example.laundry.core.model.OrderStatus
import com.example.laundry.core.repository.LaundryRepository
import kotlinx.coroutines.launch

private val vendorGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF4F7FB),
        Color(0xFFF8FAFC),
        Color(0xFFEEF6FF)
    )
)

private val vendorCard = Color(0xFFFFFFFF)
private val vendorAccent = Color(0xFF2563EB)

@Composable
private fun VendorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F172A),
            secondary = Color(0xFF14B8A6),
            background = Color(0xFFF4F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F5F9),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A)
        ),
        content = content
    )
}

enum class VendorScreen {
    OVERVIEW,
    ORDERS_LIST,
    SERVICE_PRICING,
    SALES_REPORTS
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
            VendorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedRole by remember { mutableStateOf(AuthRole.VENDOR) }
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
                        VendorMainScreen(
                            repository = repository,
                            onLogout = { isLoggedIn = false }
                        )
                    }
                }
            }
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
    var shopName by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    val roleList = listOf(AuthRole.CUSTOMER, AuthRole.VENDOR, AuthRole.DRIVER)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vendorGradient)
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
                    text = "Vendor Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (authMode) {
                        AuthMode.LOGIN -> "Secure vendor access"
                        AuthMode.SIGN_UP -> "Create your vendor account"
                        AuthMode.FORGOT_PASSWORD -> "Reset your vendor password"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    roleList.forEach { role ->
                        val label = when (role) {
                            AuthRole.CUSTOMER -> "Customer"
                            AuthRole.VENDOR -> "Vendor"
                            AuthRole.DRIVER -> "Driver"
                        }
                        FilterChip(
                            selected = role == selectedRole,
                            onClick = { onRoleChange(role) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (authMode) {
                    AuthMode.LOGIN -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Business email") },
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
                            label = { Text("Owner name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Shop name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Business email") },
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
                                onRegister(fullName, "", email, password)
                                authMessage = "Vendor account created for $shopName. Please sign in to continue."
                                authMode = AuthMode.LOGIN
                                fullName = ""
                                shopName = ""
                                password = ""
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fullName.isNotBlank() && shopName.isNotBlank() && email.isNotBlank() && password.isNotBlank()
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
                            label = { Text("Business email") },
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
fun VendorMainScreen(repository: LaundryRepository, onLogout: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    var currentSubScreen by remember { mutableStateOf(VendorScreen.OVERVIEW) }
    val orders by repository.getVendorOrders().collectAsStateWithLifecycle(initialValue = emptyList())
    var showKycDialog by remember { mutableStateOf(false) }
    var kycVerified by remember { mutableStateOf(false) }

    if (selectedTab == 0 && currentSubScreen != VendorScreen.OVERVIEW) {
        BackHandler { currentSubScreen = VendorScreen.OVERVIEW }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
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
                        Text("Vendor Studio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFFFFFFF),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; currentSubScreen = VendorScreen.OVERVIEW },
                    icon = { Icon(Icons.Default.List, "Orders") },
                    label = { Text("Orders") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, "Shop") },
                    label = { Text("Shop") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(vendorGradient)
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                when (currentSubScreen) {
                    VendorScreen.OVERVIEW -> VendorDashboard(
                        orders = orders,
                        kycVerified = kycVerified,
                        onNavigate = { currentSubScreen = it },
                        onVerifyClick = { showKycDialog = true }
                    )
                    VendorScreen.ORDERS_LIST -> OrderListScreen(repository = repository, orders = orders, onBack = { currentSubScreen = VendorScreen.OVERVIEW })
                    VendorScreen.SERVICE_PRICING -> ServicePricingEditor(repository = repository, onBack = { currentSubScreen = VendorScreen.OVERVIEW })
                    VendorScreen.SALES_REPORTS -> SalesReportsScreen(orders = orders, onBack = { currentSubScreen = VendorScreen.OVERVIEW })
                }
            } else {
                ShopManagement(repository)
            }
        }
    }

    if (showKycDialog) {
        KycVerificationDialog(
            role = "Vendor",
            onDismiss = { showKycDialog = false },
            onComplete = {
                kycVerified = true
                showKycDialog = false
            }
        )
    }
}

@Composable
fun ShopManagement(repository: LaundryRepository) {
    var shopName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFEFF6FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Shop registration", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Set up your storefront", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text("Shop Name") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { scope.launch { repository.registerShop(shopName, address) } },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register shop")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = Color(0xFF2A3B4E))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Pricing management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Manage services and pricing from the dashboard overview.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VendorDashboard(
    orders: List<Order>,
    kycVerified: Boolean,
    onNavigate: (VendorScreen) -> Unit,
    onVerifyClick: () -> Unit
) {
    val context = LocalContext.current
    val newOrdersCount = orders.count { it.status == OrderStatus.REQUESTED }
    val pendingPickupCount = orders.count { it.status == OrderStatus.PICKUP_ASSIGNED || it.status == OrderStatus.ACCEPTED }
    val completedOrders = orders.filter { it.status == OrderStatus.DELIVERED }
    val totalRevenue = completedOrders.sumOf { it.totalCents } / 100.0
    val avgOrderValue = if (completedOrders.isNotEmpty()) totalRevenue / completedOrders.size else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        KycStatusCard(
            title = "Vendor KYC",
            subtitle = if (kycVerified) "Verified and approved for payouts" else "Complete compliance check to activate payouts",
            verified = kycVerified,
            onActionClick = onVerifyClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFEAF3FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Earnings summary", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "₦${String.format("%.2f", totalRevenue)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard("Completed", completedOrders.size, Modifier.weight(1f))
                    SummaryCard("Avg. order", "₦${String.format("%.2f", avgOrderValue)}", Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("New orders", newOrdersCount, Modifier.weight(1f))
            SummaryCard("Pending pickup", pendingPickupCount, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                Toast.makeText(context, "Opening services...", Toast.LENGTH_SHORT).show()
                onNavigate(VendorScreen.SERVICE_PRICING)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage services & pricing")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                Toast.makeText(context, "Opening reports...", Toast.LENGTH_SHORT).show()
                onNavigate(VendorScreen.SALES_REPORTS)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sales reports")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = { onNavigate(VendorScreen.ORDERS_LIST) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View all orders")
        }
    }
}

@Composable
fun OrderListScreen(repository: LaundryRepository, orders: List<Order>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Order management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(orders) { order ->
                OrderListItem(order) { newStatus ->
                    scope.launch { repository.updateVendorOrderStatus(order.id, newStatus) }
                }
            }
        }
    }
}

@Composable
fun ServicePricingEditor(repository: LaundryRepository, onBack: () -> Unit) {
    var services by remember {
        mutableStateOf(
            listOf(
                LaundryService("1", "Wash & Fold", 200, "kg"),
                LaundryService("2", "Dry Clean", 500, "piece"),
                LaundryService("3", "Ironing Only", 100, "piece")
            )
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("Service pricing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(services) { service ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = vendorCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Unit: ${service.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = (service.priceCents / 100.0).toString(),
                                onValueChange = { newValue ->
                                    val price = (newValue.toDoubleOrNull() ?: 0.0) * 100
                                    services = services.map {
                                        if (it.id == service.id) it.copy(priceCents = price.toInt()) else it
                                    }
                                },
                                label = { Text("Price (₦)") },
                                modifier = Modifier.width(110.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        repository.updatePricing(services)
                        snackbarHostState.showSnackbar("Pricing updated successfully")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }
        }
    }
}

@Composable
fun SalesReportsScreen(orders: List<Order>, onBack: () -> Unit) {
    val deliveredOrders = orders.filter { it.status == OrderStatus.DELIVERED }
    val totalRevenueCents = deliveredOrders.sumOf { it.totalCents }
    val completedCount = deliveredOrders.size

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Sales reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFEAF3FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total revenue", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "₦${String.format("%.2f", totalRevenueCents / 100.0)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("from $completedCount completed orders", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = vendorCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Average order value")
                val avg = if (completedCount > 0) totalRevenueCents / completedCount / 100.0 else 0.0
                Text("₦${String.format("%.2f", avg)}", fontWeight = FontWeight.Bold)
            }
        }
    }
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
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$role KYC verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Complete the vendor compliance checks to pass verification.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("Owner name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = taxId, onValueChange = { taxId = it }, label = { Text("Tax ID / registration number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                enabled = businessName.isNotBlank() && ownerName.isNotBlank() && taxId.isNotBlank()
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
private fun SummaryCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEAF3FF)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFFFF)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun customerPhoneNumber(customerId: String): String {
    val seed = if (customerId.isBlank()) "customer" else customerId
    val number = kotlin.math.abs(seed.hashCode()) % 900000 + 100000
    return "+1 555 ${String.format("%06d", number).take(3)} ${String.format("%06d", number).drop(3)}"
}

@Composable
fun OrderListItem(order: Order, onUpdateStatus: (OrderStatus) -> Unit) {
    val context = LocalContext.current
    var showAssignRiderDialog by remember { mutableStateOf(false) }
    val riderStatus = when (order.status) {
        OrderStatus.ACCEPTED -> OrderStatus.PICKUP_ASSIGNED
        OrderStatus.READY_FOR_DELIVERY -> OrderStatus.DELIVERY_ASSIGNED
        else -> null
    }
    val customerPhone = remember(order.customerId) { customerPhoneNumber(order.customerId) }
    val customerStatusMessage = remember(order.status) { "Your laundry order is now ${order.status.name.lowercase().replace('_', ' ')}. Please keep an eye out for the next update." }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = vendorCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.id.takeLast(6)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(order.status.name, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$customerPhone")).apply {
                            putExtra("sms_body", customerStatusMessage)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SMS update")
                }
                OutlinedButton(
                    onClick = {
                        val message = Uri.encode(customerStatusMessage)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${customerPhone.filter { it.isDigit() }}?text=$message"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("WhatsApp")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                order.status == OrderStatus.REQUESTED -> {
                    Button(onClick = { onUpdateStatus(OrderStatus.ACCEPTED) }, shape = RoundedCornerShape(14.dp)) {
                        Text("Accept order")
                    }
                }
                order.status == OrderStatus.ACCEPTED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAssignRiderDialog = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("Assign pickup rider")
                        }
                        OutlinedButton(onClick = { onUpdateStatus(OrderStatus.PICKUP_ASSIGNED) }, shape = RoundedCornerShape(14.dp)) {
                            Text("Skip rider")
                        }
                    }
                }
                order.status == OrderStatus.AT_VENDOR -> {
                    Button(onClick = { onUpdateStatus(OrderStatus.PROCESSING) }, shape = RoundedCornerShape(14.dp)) {
                        Text("Start processing")
                    }
                }
                order.status == OrderStatus.PROCESSING -> {
                    Button(onClick = { onUpdateStatus(OrderStatus.READY_FOR_DELIVERY) }, shape = RoundedCornerShape(14.dp)) {
                        Text("Ready for delivery")
                    }
                }
                order.status == OrderStatus.READY_FOR_DELIVERY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAssignRiderDialog = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("Assign delivery rider")
                        }
                        OutlinedButton(onClick = { onUpdateStatus(OrderStatus.DELIVERY_ASSIGNED) }, shape = RoundedCornerShape(14.dp)) {
                            Text("Skip rider")
                        }
                    }
                }
                order.status == OrderStatus.PICKUP_ASSIGNED -> {
                    Text("Pickup rider assigned and en route.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                order.status == OrderStatus.DELIVERY_ASSIGNED -> {
                    Text("Delivery rider assigned and en route.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showAssignRiderDialog && riderStatus != null) {
        RiderAssignmentDialog(
            assignmentLabel = if (riderStatus == OrderStatus.PICKUP_ASSIGNED) "Assign pickup rider" else "Assign delivery rider",
            onDismiss = { showAssignRiderDialog = false },
            onAssign = { riderName ->
                showAssignRiderDialog = false
                Toast.makeText(
                    context,
                    "$riderName assigned for ${if (riderStatus == OrderStatus.PICKUP_ASSIGNED) "pickup" else "delivery"}",
                    Toast.LENGTH_SHORT
                ).show()
                onUpdateStatus(riderStatus)
            }
        )
    }
}

@Composable
private fun RiderAssignmentDialog(
    assignmentLabel: String,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    var selectedRider by remember { mutableStateOf("Nora") }
    val riders = listOf("Nora", "Ali", "Sam", "Maya")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(assignmentLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the rider for this order.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                riders.forEach { rider ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(rider)
                        RadioButton(
                            selected = selectedRider == rider,
                            onClick = { selectedRider = rider }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAssign(selectedRider) }, shape = RoundedCornerShape(14.dp)) {
                Text("Assign rider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
