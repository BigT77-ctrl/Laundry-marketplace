package com.example.laundry.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.laundry.core.api.RetrofitClient
import com.example.laundry.core.model.Order
import com.example.laundry.core.model.OrderStatus
import com.example.laundry.core.repository.LaundryRepository
import kotlinx.coroutines.launch

private val driverGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF4F7FB),
        Color(0xFFF8FAFC),
        Color(0xFFEFF6FF)
    )
)

@Composable
private fun DriverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F172A),
            secondary = Color(0xFF2563EB),
            background = Color(0xFFF4F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F5F9),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A)
        ),
        content = content
    )
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
            DriverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedRole by remember { mutableStateOf(AuthRole.DRIVER) }
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
                        DriverDashboard(
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
    var phoneNumber by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    val roleList = listOf(AuthRole.CUSTOMER, AuthRole.VENDOR, AuthRole.DRIVER)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(driverGradient)
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
                    text = "Driver Portal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (authMode) {
                        AuthMode.LOGIN -> "Secure rider access"
                        AuthMode.SIGN_UP -> "Join our driver network"
                        AuthMode.FORGOT_PASSWORD -> "Reset your rider password"
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
                            label = { Text("Driver email") },
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
                                authMessage = "Driver account created for $fullName. Please sign in to continue."
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
                            label = { Text("Driver email") },
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

@Composable
fun DriverDashboard(repository: LaundryRepository, onLogout: () -> Unit = {}) {
    var isOnline by remember { mutableStateOf(false) }
    var showKycDialog by remember { mutableStateOf(false) }
    var kycVerified by remember { mutableStateOf(false) }
    var selectedAssignment by remember { mutableStateOf<Order?>(null) }
    val assignments by repository.getDriverAssignments().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val pickupCount = assignments.count { it.status == OrderStatus.PICKUP_ASSIGNED || it.status == OrderStatus.PICKED_UP }
    val deliveryCount = assignments.count { it.status == OrderStatus.DELIVERY_ASSIGNED || it.status == OrderStatus.OUT_FOR_DELIVERY }
    val nextStopTitle = assignments.firstOrNull()?.status?.name ?: "No scheduled stops"

    if (selectedAssignment != null) {
        RiderAssignmentStatusScreen(
            order = selectedAssignment!!,
            onBack = { selectedAssignment = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(driverGradient)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.laundry_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }
        Text(
            text = "Driver Portal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isOnline) Color(0xFFE0F2FE) else Color(0xFFFFFFFF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duty status", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (isOnline) "Available for pickups" else "Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = isOnline, onCheckedChange = { isOnline = it })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        KycStatusCard(
            title = "Driver KYC",
            subtitle = if (kycVerified) "Verified and cleared for routes" else "Complete driver verification to accept jobs",
            verified = kycVerified,
            onActionClick = { showKycDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isOnline) {
            RouteSummaryCard(
                pickupCount = pickupCount,
                deliveryCount = deliveryCount,
                nextStop = nextStopTitle
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Active assignments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(assignments) { order ->
                    DriverOrderListItem(
                        order,
                        onViewStatus = { selectedAssignment = order },
                        onUpdateStatus = { newStatus ->
                            scope.launch { repository.updateDriverOrderStatus(order.id, newStatus) }
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Go online to receive delivery requests", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showKycDialog) {
        KycVerificationDialog(
            role = "Driver",
            onDismiss = { showKycDialog = false },
            onComplete = {
                kycVerified = true
                showKycDialog = false
            }
        )
    }
}

@Composable
private fun RouteSummaryCard(pickupCount: Int, deliveryCount: Int, nextStop: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Route summary", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = nextStop,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryMetricCard("Pickups", pickupCount.toString(), Modifier.weight(1f))
                SummaryMetricCard("Deliveries", deliveryCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEAF3FF)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2563EB))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
    var driverName by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$role KYC verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Complete the driving and identity checks to pass verification.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = driverName, onValueChange = { driverName = it }, label = { Text("Driver full name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = { Text("License number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = vehicleNumber, onValueChange = { vehicleNumber = it }, label = { Text("Vehicle registration") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                enabled = driverName.isNotBlank() && licenseNumber.isNotBlank() && vehicleNumber.isNotBlank()
            ) {
                Text("Pass KYC")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun customerPhoneNumber(customerId: String): String {
    val seed = if (customerId.isBlank()) "customer" else customerId
    val number = kotlin.math.abs(seed.hashCode()) % 900000 + 100000
    return "+1 555 ${String.format("%06d", number).take(3)} ${String.format("%06d", number).drop(3)}"
}

@Composable
fun DriverOrderListItem(
    order: Order,
    onViewStatus: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit
) {
    val context = LocalContext.current
    val customerPhone = remember(order.customerId) { customerPhoneNumber(order.customerId) }
    val customerStatusMessage = remember(order.status) { "Your laundry order status has changed to ${order.status.name.lowercase().replace('_', ' ')}. Please check the app or reply to this message if you need help." }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.id.takeLast(6)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusBadge(order.status)
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

            Button(
                onClick = onViewStatus,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View rider status")
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (order.status) {
                OrderStatus.PICKUP_ASSIGNED -> {
                    ActionButton(Icons.Default.LocationOn, "Confirm pickup") { onUpdateStatus(OrderStatus.PICKED_UP) }
                }
                OrderStatus.PICKED_UP -> {
                    ActionButton(Icons.Default.CheckCircle, "Arrived at vendor") { onUpdateStatus(OrderStatus.AT_VENDOR) }
                }
                OrderStatus.DELIVERY_ASSIGNED -> {
                    ActionButton(Icons.Default.LocationOn, "Confirm delivery pickup") { onUpdateStatus(OrderStatus.OUT_FOR_DELIVERY) }
                }
                OrderStatus.OUT_FOR_DELIVERY -> {
                    ActionButton(Icons.Default.CheckCircle, "Mark as delivered") { onUpdateStatus(OrderStatus.DELIVERED) }
                }
                else -> {
                    Text("Waiting for next step...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RiderAssignmentStatusScreen(order: Order, onBack: () -> Unit) {
    val context = LocalContext.current
    val customerPhone = remember(order.customerId) { customerPhoneNumber(order.customerId) }
    val riderName = remember(order.id) { listOf("Nora", "Ali", "Sam", "Maya").random() }
    val currentStep = when (order.status) {
        OrderStatus.PICKUP_ASSIGNED -> "Pickup assigned to rider"
        OrderStatus.PICKED_UP -> "Laundry collected from customer"
        OrderStatus.AT_VENDOR -> "At vendor facility - processing"
        OrderStatus.PROCESSING -> "Laundry in progress"
        OrderStatus.READY_FOR_DELIVERY -> "Ready for final delivery"
        OrderStatus.DELIVERY_ASSIGNED -> "Delivery rider assigned"
        OrderStatus.OUT_FOR_DELIVERY -> "Rider is on the way"
        OrderStatus.DELIVERED -> "Delivered to customer"
        else -> "Order awaiting pickup"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(driverGradient)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Rider assignment status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFFFFF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Order #${order.id.takeLast(6)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                StatusBadge(order.status)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Current stage", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2563EB))
                Text(currentStep, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Assigned rider: $riderName", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text("Customer: $customerPhone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFEAF3FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Route progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                val stages = listOf(
                    "Pickup request",
                    "Customer collected",
                    "Vendor handoff",
                    "Delivery in transit",
                    "Customer delivered"
                )
                val activeIndex = when (order.status) {
                    OrderStatus.PICKUP_ASSIGNED, OrderStatus.PICKED_UP -> 1
                    OrderStatus.AT_VENDOR, OrderStatus.PROCESSING, OrderStatus.READY_FOR_DELIVERY -> 2
                    OrderStatus.DELIVERY_ASSIGNED, OrderStatus.OUT_FOR_DELIVERY -> 3
                    OrderStatus.DELIVERED -> 4
                    else -> 0
                }
                stages.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = if (index <= activeIndex) Color(0xFF2563EB) else Color(0xFFDCE9FF)
                        ) {
                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (index <= activeIndex) {
                                    Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, color = if (index <= activeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index < stages.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Call customer")
        }
    }
}

@Composable
fun StatusBadge(status: OrderStatus) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
