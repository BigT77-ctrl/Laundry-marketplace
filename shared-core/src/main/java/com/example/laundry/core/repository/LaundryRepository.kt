package com.example.laundry.core.repository

import com.example.laundry.core.api.LaundryApiService
import com.example.laundry.core.api.CreateOrderRequest
import com.example.laundry.core.api.StatusUpdateRequest
import com.example.laundry.core.api.ShopRegistrationRequest
import com.example.laundry.core.api.ReviewRequest
import com.example.laundry.core.api.DisputeRequest
import com.example.laundry.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LaundryRepository(private val apiService: LaundryApiService) {

    // Customer Actions
    fun getCustomerOrders(): Flow<List<Order>> = flow {
        val response = apiService.getCustomerOrders()
        if (response.isSuccessful) {
            emit(response.body() ?: emptyList())
        } else {
            throw Exception("Failed to fetch customer orders")
        }
    }

    suspend fun createOrder(vendorId: String, items: List<com.example.laundry.core.api.OrderItemRequest>): Order? {
        val response = apiService.createOrder(CreateOrderRequest(vendorId, items))
        return if (response.isSuccessful) response.body() else null
    }

    // Vendor Actions
    fun getVendorOrders(): Flow<List<Order>> = flow {
        val response = apiService.getVendorOrders()
        if (response.isSuccessful) {
            emit(response.body() ?: emptyList())
        } else {
            throw Exception("Failed to fetch vendor orders")
        }
    }

    suspend fun updateVendorOrderStatus(orderId: String, status: OrderStatus): Order? {
        val response = apiService.updateVendorOrderStatus(orderId, StatusUpdateRequest(status))
        return if (response.isSuccessful) response.body() else null
    }

    // Driver Actions
    fun getDriverAssignments(): Flow<List<Order>> = flow {
        val response = apiService.getDriverAssignments()
        if (response.isSuccessful) {
            emit(response.body() ?: emptyList())
        } else {
            throw Exception("Failed to fetch driver assignments")
        }
    }

    suspend fun updateDriverOrderStatus(orderId: String, status: OrderStatus): Order? {
        val response = apiService.updateDriverOrderStatus(orderId, StatusUpdateRequest(status))
        return if (response.isSuccessful) response.body() else null
    }

    // General Marketplace Actions
    suspend fun getShops(): List<Shop> {
        val response = apiService.getShops()
        return if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
    }

    suspend fun registerShop(name: String, address: String): Shop? {
        val response = apiService.registerShop(ShopRegistrationRequest(name, address))
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun updatePricing(services: List<LaundryService>): List<LaundryService> {
        val response = apiService.updatePricing(services)
        return if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
    }

    suspend fun submitReview(orderId: String, rating: Int, comment: String): Review? {
        val response = apiService.submitReview(orderId, ReviewRequest(rating, comment))
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun openDispute(orderId: String, reason: String): Dispute? {
        val response = apiService.openDispute(orderId, DisputeRequest(reason))
        return if (response.isSuccessful) response.body() else null
    }
}
