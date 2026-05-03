package com.example.laundry.vendor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    VendorMainScreen(repository)
                }
            }
        }
    }
}

@Composable
fun VendorMainScreen(repository: LaundryRepository) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, "Orders") },
                    label = { Text("Orders") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, "Shop") },
                    label = { Text("Shop Setup") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> VendorDashboard(repository)
                1 -> ShopManagement(repository)
            }
        }
    }
}

@Composable
fun ShopManagement(repository: LaundryRepository) {
    var shopName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shop Registration", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text("Shop Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    repository.registerShop(shopName, address)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register Shop")
        }
        
        Divider(modifier = Modifier.padding(vertical = 24.dp))
        
        Text("Pricing Management", style = MaterialTheme.typography.titleLarge)
        // TODO: List and update LaundryServices
    }
}

@Composable
fun VendorDashboard(repository: LaundryRepository) {
    val orders by repository.getVendorOrders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Order Management", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(orders) { order ->
                OrderListItem(order) { newStatus ->
                    scope.launch {
                        repository.updateVendorOrderStatus(order.id, newStatus)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderListItem(order: Order, onUpdateStatus: (OrderStatus) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order ID: ${order.id.takeLast(6)}")
                Text(order.status.name, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (order.status == OrderStatus.REQUESTED) {
                Button(onClick = { onUpdateStatus(OrderStatus.ACCEPTED) }) {
                    Text("Accept Order")
                }
            } else if (order.status == OrderStatus.AT_VENDOR) {
                Button(onClick = { onUpdateStatus(OrderStatus.PROCESSING) }) {
                    Text("Start Processing")
                }
            } else if (order.status == OrderStatus.PROCESSING) {
                Button(onClick = { onUpdateStatus(OrderStatus.READY_FOR_DELIVERY) }) {
                    Text("Ready for Delivery")
                }
            }
        }
    }
}
