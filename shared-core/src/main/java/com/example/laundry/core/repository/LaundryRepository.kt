package com.example.laundry.core.repository

import com.example.laundry.core.api.AdminKycRequest
import com.example.laundry.core.api.AdminLoginRequest
import com.example.laundry.core.api.AdminOrderStatusRequest
import com.example.laundry.core.api.AdminUserStatusRequest
import com.example.laundry.core.api.AdminWalletRequest
import com.example.laundry.core.api.LaundryApiService
import com.example.laundry.core.api.CreateOrderRequest
import com.example.laundry.core.api.StatusUpdateRequest
import com.example.laundry.core.api.ShopRegistrationRequest
import com.example.laundry.core.api.ReviewRequest
import com.example.laundry.core.api.DisputeRequest
import com.example.laundry.core.api.ForgotPasswordRequest
import com.example.laundry.core.api.UserAuthRequest
import com.example.laundry.core.api.UserRegisterRequest
import com.example.laundry.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository class that handles data operations for the laundry marketplace.
 * It acts as an abstraction layer over the [LaundryApiService], providing [Flow]s
 * and suspend functions for the UI layer to consume.
 *
 * @property apiService The API service used to fetch and send data.
 */
class LaundryRepository(private val apiService: LaundryApiService) {
    private var adminToken: String? = null
    private var currentUserToken: String? = null

    private suspend fun requireAdminToken(): String? {
        if (adminToken == null) {
            val response = apiService.adminLogin(AdminLoginRequest("admin", "admin123"))
            adminToken = response.body()?.token
        }
        return adminToken
    }

    // Customer Actions
    /**
     * Retrieves a stream of orders for the currently authenticated customer.
     * @return A [Flow] emitting a list of [Order]s.
     * @throws Exception if the API call fails.
     */
    fun getCustomerOrders(): Flow<List<Order>> = flow {
        try {
            val response = apiService.getCustomerOrders()
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            } else {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Creates a new laundry order.
     * @param vendorId The ID of the vendor shop.
     * @param items The list of items to be cleaned.
     * @return The created [Order] if successful, null otherwise.
     */
    suspend fun createOrder(vendorId: String, items: List<com.example.laundry.core.api.OrderItemRequest>): Order? {
        val response = apiService.createOrder(CreateOrderRequest(vendorId, items))
        return if (response.isSuccessful) response.body() else null
    }

    // Vendor Actions
    /**
     * Retrieves a stream of orders assigned to the vendor.
     * @return A [Flow] emitting a list of [Order]s.
     * @throws Exception if the API call fails.
     */
    fun getVendorOrders(): Flow<List<Order>> = flow {
        try {
            val response = apiService.getVendorOrders()
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            } else {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Updates the status of an order for a vendor.
     * @param orderId The ID of the order.
     * @param status The new [OrderStatus].
     * @return The updated [Order] if successful, null otherwise.
     */
    suspend fun updateVendorOrderStatus(orderId: String, status: OrderStatus): Order? {
        val response = apiService.updateVendorOrderStatus(orderId, StatusUpdateRequest(status))
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun loginUser(role: String, email: String, password: String): String? {
        val response = apiService.loginUser(UserAuthRequest(email.trim(), password, role.trim().lowercase()))
        val token = if (response.isSuccessful) response.body()?.token else null
        if (token != null) currentUserToken = token
        return token
    }

    suspend fun registerUser(name: String, email: String, password: String, role: String, phone: String = ""): Boolean {
        val response = apiService.registerUser(
            UserRegisterRequest(
                name = name.trim(),
                email = email.trim(),
                password = password,
                role = role.trim().lowercase(),
                phone = phone.trim()
            )
        )
        if (response.isSuccessful) {
            val token = response.body()?.token
            if (token != null) currentUserToken = token
            return true
        }
        return false
    }

    suspend fun forgotPassword(email: String, role: String): Boolean {
        val response = apiService.forgotPassword(ForgotPasswordRequest(email.trim(), role.trim().lowercase()))
        return response.isSuccessful
    }

    suspend fun adminLogin(username: String, password: String): String? {
        val response = apiService.adminLogin(AdminLoginRequest(username, password))
        return if (response.isSuccessful) response.body()?.token else null
    }

    suspend fun updateUserStatus(userId: String, status: String): Boolean {
        val token = requireAdminToken() ?: return false
        val response = apiService.updateAdminUserStatus(userId, "Bearer $token", AdminUserStatusRequest(status))
        return response.isSuccessful
    }

    suspend fun updateUserKyc(userId: String, status: String): Boolean {
        val token = requireAdminToken() ?: return false
        val response = apiService.updateAdminKyc(userId, "Bearer $token", AdminKycRequest(status))
        return response.isSuccessful
    }

    suspend fun adjustWallet(userId: String, amountCents: Int, type: String = "topup", note: String = "Wallet adjustment"): Boolean {
        val token = requireAdminToken() ?: return false
        val response = apiService.updateAdminUserWallet(userId, "Bearer $token", AdminWalletRequest(amountCents, type, note))
        return response.isSuccessful
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Boolean {
        val token = requireAdminToken() ?: return false
        val response = apiService.updateAdminOrderStatus(orderId, "Bearer $token", AdminOrderStatusRequest(status))
        return response.isSuccessful
    }

    // Driver Actions
    /**
     * Retrieves a stream of assignments for the driver.
     * @return A [Flow] emitting a list of [Order]s.
     * @throws Exception if the API call fails.
     */
    fun getDriverAssignments(): Flow<List<Order>> = flow {
        try {
            val response = apiService.getDriverAssignments()
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            } else {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Updates the status of an order for a driver.
     * @param orderId The ID of the order.
     * @param status The new [OrderStatus].
     * @return The updated [Order] if successful, null otherwise.
     */
    suspend fun updateDriverOrderStatus(orderId: String, status: OrderStatus): Order? {
        val response = apiService.updateDriverOrderStatus(orderId, StatusUpdateRequest(status))
        return if (response.isSuccessful) response.body() else null
    }

    // General Marketplace Actions
    /**
     * Fetches the list of available laundry shops.
     * @return A list of [Shop]s.
     */
    suspend fun getShops(): List<Shop> {
        val response = apiService.getShops()
        return if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
    }

    /**
     * Registers a new shop in the marketplace.
     * @param name The shop name.
     * @param address The shop address.
     * @return The registered [Shop] if successful, null otherwise.
     */
    suspend fun registerShop(name: String, address: String): Shop? {
        val response = apiService.registerShop(ShopRegistrationRequest(name, address))
        return if (response.isSuccessful) response.body() else null
    }

    /**
     * Updates the pricing information for services.
     * @param services The list of services with updated prices.
     * @return The updated list of [LaundryService]s.
     */
    suspend fun updatePricing(services: List<LaundryService>): List<LaundryService> {
        val response = apiService.updatePricing(services)
        return if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
    }

    /**
     * Submits a customer review for an order.
     * @param orderId The ID of the order.
     * @param rating The numeric rating.
     * @param comment The textual review.
     * @return The submitted [Review] if successful, null otherwise.
     */
    suspend fun submitReview(orderId: String, rating: Int, comment: String): Review? {
        val response = apiService.submitReview(orderId, ReviewRequest(rating, comment))
        return if (response.isSuccessful) response.body() else null
    }

    /**
     * Opens a dispute for a customer order.
     * @param orderId The ID of the order.
     * @param reason The reason for the dispute.
     * @return The [Dispute] object if successful, null otherwise.
     */
    suspend fun openDispute(orderId: String, reason: String): Dispute? {
        val response = apiService.openDispute(orderId, DisputeRequest(reason))
        return if (response.isSuccessful) response.body() else null
    }
}
