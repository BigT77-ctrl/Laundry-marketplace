package com.example.laundry.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.laundry.core.api.RetrofitClient
import com.example.laundry.core.model.Order
import com.example.laundry.core.model.OrderStatus
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
                    DriverDashboard(repository)
                }
            }
        }
    }
}

@Composable
fun DriverDashboard(repository: LaundryRepository) {
    var isOnline by remember { mutableStateOf(false) }
    val assignments by repository.getDriverAssignments().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Driver Portal",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duty Status", style = MaterialTheme.typography.titleMedium)
                    Text(if (isOnline) "Available for Pickups" else "Offline", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = isOnline, onCheckedChange = { isOnline = it })
            }
        }

        if (isOnline) {
            Text("Active Assignments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(assignments) { order ->
                    DriverOrderListItem(order) { newStatus ->
                        scope.launch {
                            repository.updateDriverOrderStatus(order.id, newStatus)
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Go online to receive delivery requests")
                }
            }
        }
    }
}

@Composable
fun DriverOrderListItem(order: Order, onUpdateStatus: (OrderStatus) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order #${order.id.takeLast(6)}", style = MaterialTheme.typography.titleSmall)
                StatusBadge(order.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Contextual Action Button based on Logistics Flow
            when (order.status) {
                OrderStatus.PICKUP_ASSIGNED -> {
                    ActionButton(Icons.Default.LocationOn, "Confirm Pickup") {
                        onUpdateStatus(OrderStatus.PICKED_UP)
                    }
                }
                OrderStatus.PICKED_UP -> {
                    ActionButton(Icons.Default.CheckCircle, "Arrived at Vendor") {
                        onUpdateStatus(OrderStatus.AT_VENDOR)
                    }
                }
                OrderStatus.DELIVERY_ASSIGNED -> {
                    ActionButton(Icons.Default.LocationOn, "Confirm Delivery Pickup") {
                        onUpdateStatus(OrderStatus.OUT_FOR_DELIVERY)
                    }
                }
                OrderStatus.OUT_FOR_DELIVERY -> {
                    ActionButton(Icons.Default.CheckCircle, "Mark as Delivered") {
                        onUpdateStatus(OrderStatus.DELIVERED)
                    }
                }
                else -> {
                    Text("Waiting for next step...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: OrderStatus) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
