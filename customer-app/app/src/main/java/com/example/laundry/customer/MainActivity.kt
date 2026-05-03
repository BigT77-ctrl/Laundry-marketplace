package com.example.laundry.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.laundry.core.api.OrderItemRequest
import com.example.laundry.core.api.RetrofitClient
import com.example.laundry.core.model.*
import com.example.laundry.core.repository.LaundryRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository = LaundryRepository(RetrofitClient.apiService)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(repository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: LaundryRepository) {
    var currentTab by remember { mutableStateOf(0) }
    var selectedShop by remember { mutableStateOf<Shop?>(null) }
    
    Scaffold(
        bottomBar = {
            if (selectedShop == null) {
                NavigationBar {
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
                        label = { Text("My Orders") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedShop != null) {
                BookingScreen(selectedShop!!, repository, onBack = { selectedShop = null })
            } else {
                if (currentTab == 0) {
                    ShopBrowser(repository, onShopSelected = { selectedShop = it })
                } else {
                    CustomerDashboard(repository)
                }
            }
        }
    }
}

@Composable
fun ShopBrowser(repository: LaundryRepository, onShopSelected: (Shop) -> Unit) {
    var shops by remember { mutableStateOf(emptyList<Shop>()) }

    LaunchedEffect(Unit) {
        shops = repository.getShops()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Browse Laundry Shops", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(shops) { shop ->
                ShopItem(shop) { onShopSelected(shop) }
            }
        }
    }
}

@Composable
fun ShopItem(shop: Shop, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(shop.name, style = MaterialTheme.typography.titleMedium)
                Text(shop.address, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                Text(shop.rating.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(shop: Shop, repository: LaundryRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val services = if (shop.services.isEmpty()) {
        listOf(
            LaundryService("1", "Wash & Fold", 500, "kg"),
            LaundryService("2", "Dry Cleaning", 1200, "piece"),
            LaundryService("3", "Ironing", 300, "piece")
        )
    } else shop.services

    var selectedQuantities by remember { mutableStateOf(services.associate { it.id to 0 }) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(shop.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(services) { service ->
                val quantity = selectedQuantities[service.id] ?: 0
                ServiceItem(service, quantity) { newQty ->
                    selectedQuantities = selectedQuantities.toMutableMap().apply { put(service.id, newQty) }
                }
            }
        }

        val totalCents = services.sumOf { (selectedQuantities[it.id] ?: 0) * it.priceCents }
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total: $${totalCents / 100.0}", style = MaterialTheme.typography.headlineSmall)
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
                    enabled = totalCents > 0
                ) {
                    Text("Book Now")
                }
            }
        }
    }
}

@Composable
fun ServiceItem(service: LaundryService, quantity: Int, onQuantityChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(service.name, style = MaterialTheme.typography.titleMedium)
            Text("$${service.priceCents / 100.0} per ${service.unit}", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (quantity > 0) onQuantityChange(quantity - 1) }) {
                Text("-", style = MaterialTheme.typography.headlineSmall)
            }
            Text(quantity.toString(), modifier = Modifier.padding(horizontal = 8.dp))
            TextButton(onClick = { onQuantityChange(quantity + 1) }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
fun CustomerDashboard(repository: LaundryRepository) {
    val orders by repository.getCustomerOrders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Track Your Orders", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(orders) { order ->
                CustomerOrderListItem(order, 
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
}

@Composable
fun CustomerOrderListItem(order: Order, onRate: (Int, String) -> Unit, onDispute: (String) -> Unit) {
    var showRatingDialog by remember { mutableStateOf(false) }
    var showDisputeDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order #${order.id.takeLast(4)}", style = MaterialTheme.typography.titleSmall)
                Text(order.status.name, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { getStatusProgress(order.status) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (order.status == OrderStatus.DELIVERED) {
                    Button(onClick = { showRatingDialog = true }) {
                        Text("Rate")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Allow dispute if not cancelled and after it starts
                if (order.status != OrderStatus.CANCELLED && order.status != OrderStatus.REQUESTED) {
                    OutlinedButton(onClick = { showDisputeDialog = true }) {
                        Text("Dispute", color = MaterialTheme.colorScheme.error)
                    }
                }
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
fun DisputeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a Dispute") },
        text = {
            Column {
                Text("Please explain the issue with your order (e.g., missing items, late delivery).", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Dispute") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Submit Dispute")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
                                tint = if (index <= rating) Color(0xFFFFB300) else Color.LightGray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(rating, comment) }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getStatusProgress(status: OrderStatus): Float {
    return when(status) {
        OrderStatus.REQUESTED -> 0.1f
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
