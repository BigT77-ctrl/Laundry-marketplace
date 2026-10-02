package com.example.laundry.core.api

import com.example.laundry.core.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface defining the API endpoints for the laundry marketplace.
 * This includes endpoints for customers, vendors, and drivers.
 */
interface LaundryApiService {
    @POST("api/auth/login")
    suspend fun loginUser(@Body credentials: UserAuthRequest): Response<UserAuthResponse>

    @POST("api/auth/register")
    suspend fun registerUser(@Body request: UserRegisterRequest): Response<UserAuthResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Map<String, String>>

    @POST("api/admin/login")
    suspend fun adminLogin(@Body credentials: AdminLoginRequest): Response<AdminLoginResponse>

    @GET("api/admin/users")
    suspend fun getAdminUsers(
        @Header("Authorization") authorization: String
    ): Response<List<AdminUser>>

    @PATCH("api/admin/users/{userId}/status")
    suspend fun updateAdminUserStatus(
        @Path("userId") userId: String,
        @Header("Authorization") authorization: String,
        @Body status: AdminUserStatusRequest
    ): Response<AdminUser>

    @PATCH("api/admin/users/{userId}/wallet")
    suspend fun updateAdminUserWallet(
        @Path("userId") userId: String,
        @Header("Authorization") authorization: String,
        @Body request: AdminWalletRequest
    ): Response<AdminWalletResponse>

    @PATCH("api/admin/kyc/{userId}")
    suspend fun updateAdminKyc(
        @Path("userId") userId: String,
        @Header("Authorization") authorization: String,
        @Body request: AdminKycRequest
    ): Response<AdminUser>

    @PATCH("api/admin/orders/{orderId}/status")
    suspend fun updateAdminOrderStatus(
        @Path("orderId") orderId: String,
        @Header("Authorization") authorization: String,
        @Body request: AdminOrderStatusRequest
    ): Response<AdminOrderStatusResponse>

    // Customer Endpoints
    /**
     * Creates a new laundry order for a customer.
     * @param orderRequest The details of the order to be created.
     * @return A [Response] containing the created [Order].
     */
    @POST("customer/orders")
    suspend fun createOrder(@Body orderRequest: CreateOrderRequest): Response<Order>

    /**
     * Retrieves all orders for the currently authenticated customer.
     * @return A [Response] containing a list of [Order]s.
     */
    @GET("customer/orders")
    suspend fun getCustomerOrders(): Response<List<Order>>

    /**
     * Retrieves a list of available laundry shops.
     * @return A [Response] containing a list of [Shop]s.
     */
    @GET("customer/shops")
    suspend fun getShops(): Response<List<Shop>>

    /**
     * Submits a review for a completed order.
     * @param orderId The ID of the order being reviewed.
     * @param review The review details including rating and comment.
     * @return A [Response] containing the submitted [Review].
     */
    @POST("customer/orders/{orderId}/review")
    suspend fun submitReview(@Path("orderId") orderId: String, @Body review: ReviewRequest): Response<Review>

    /**
     * Opens a dispute for a specific order.
     * @param orderId The ID of the order to dispute.
     * @param dispute The reason for the dispute.
     * @return A [Response] containing the [Dispute] details.
     */
    @POST("customer/orders/{orderId}/dispute")
    suspend fun openDispute(@Path("orderId") orderId: String, @Body dispute: DisputeRequest): Response<Dispute>

    // Vendor Endpoints
    /**
     * Registers a new laundry shop for a vendor.
     * @param registration The shop registration details.
     * @return A [Response] containing the registered [Shop].
     */
    @POST("vendor/register")
    suspend fun registerShop(@Body registration: ShopRegistrationRequest): Response<Shop>

    /**
     * Retrieves all orders assigned to the vendor's shop.
     * @return A [Response] containing a list of [Order]s.
     */
    @GET("vendor/orders")
    suspend fun getVendorOrders(): Response<List<Order>>

    /**
     * Updates the status of an order from the vendor's perspective.
     * @param orderId The ID of the order to update.
     * @param statusUpdate The new status for the order.
     * @return A [Response] containing the updated [Order].
     */
    @PATCH("vendor/orders/{orderId}/status")
    suspend fun updateVendorOrderStatus(
        @Path("orderId") orderId: String,
        @Body statusUpdate: StatusUpdateRequest
    ): Response<Order>

    /**
     * Updates the pricing for the services offered by the vendor's shop.
     * @param services The updated list of laundry services and their prices.
     * @return A [Response] containing the updated list of [LaundryService]s.
     */
    @PUT("vendor/pricing")
    suspend fun updatePricing(@Body services: List<LaundryService>): Response<List<LaundryService>>

    // Driver Endpoints
    /**
     * Retrieves orders assigned to the driver for pickup or delivery.
     * @return A [Response] containing a list of [Order]s.
     */
    @GET("driver/assignments")
    suspend fun getDriverAssignments(): Response<List<Order>>

    /**
     * Updates the status of an order from the driver's perspective (e.g., picked up, delivered).
     * @param orderId The ID of the order to update.
     * @param statusUpdate The new status for the order.
     * @return A [Response] containing the updated [Order].
     */
    @PATCH("driver/orders/{orderId}/status")
    suspend fun updateDriverOrderStatus(
        @Path("orderId") orderId: String,
        @Body statusUpdate: StatusUpdateRequest
    ): Response<Order>
}

/**
 * Request body for creating a new laundry order.
 * @property vendorId The ID of the shop where the order is placed.
 * @property items List of items included in the order.
 */
data class CreateOrderRequest(
    val vendorId: String,
    val items: List<OrderItemRequest>
)

/**
 * Represents a single item in an order request.
 * @property name The name of the laundry item (e.g., "Shirt", "Pants").
 * @property quantity The number of items of this type.
 */
data class OrderItemRequest(
    val name: String,
    val quantity: Int
)

/**
 * Request body for updating the status of an order.
 * @property status The new status to be applied.
 */
data class StatusUpdateRequest(
    val status: OrderStatus
)

/**
 * Request body for registering a new laundry shop.
 * @property name The name of the shop.
 * @property address The physical address of the shop.
 */
data class ShopRegistrationRequest(
    val name: String,
    val address: String
)

/**
 * Request body for submitting an order review.
 * @property rating The rating given by the customer (e.g., 1 to 5).
 * @property comment Optional feedback or comments from the customer.
 */
data class ReviewRequest(
    val rating: Int,
    val comment: String
)

/**
 * Request body for opening a dispute on an order.
 * @property reason The reason for opening the dispute.
 */
data class DisputeRequest(
    val reason: String
)

data class UserAuthRequest(
    val email: String,
    val password: String,
    val role: String
)

data class UserRegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val phone: String? = null
)

data class ForgotPasswordRequest(
    val email: String,
    val role: String
)

data class UserAuthResponse(
    val token: String,
    val user: UserAuthUser,
    val message: String? = null
)

data class UserAuthUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String? = null,
    val kycStatus: String? = null,
    val walletBalanceCents: Int? = 0,
    val phone: String? = null
)

data class AdminLoginRequest(
    val username: String,
    val password: String
)

data class AdminLoginResponse(
    val token: String,
    val admin: AdminLoginAdmin
)

data class AdminLoginAdmin(
    val username: String,
    val role: String,
    val name: String
)

data class AdminUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val kycStatus: String,
    val kycScore: Int,
    val walletBalanceCents: Int,
    val createdAt: String
)

data class AdminUserStatusRequest(
    val status: String
)

data class AdminWalletRequest(
    val amountCents: Int,
    val type: String = "topup",
    val note: String = "Wallet adjustment"
)

data class AdminWalletResponse(
    val message: String,
    val user: AdminUser,
    val deltaCents: Int
)

data class AdminKycRequest(
    val status: String,
    val notes: String? = null
)

data class AdminOrderStatusRequest(
    val status: String
)

data class AdminOrderStatusResponse(
    val message: String,
    val order: AdminOrder
)

data class AdminOrder(
    val id: String,
    val customer_id: String?,
    val customer_name: String,
    val vendor_id: String?,
    val vendor_name: String,
    val driver_id: String?,
    val driver_name: String?,
    val status: String,
    val total_cents: Int,
    val created_at: String
)
