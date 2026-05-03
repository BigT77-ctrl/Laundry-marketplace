package com.example.laundry.core.api

import com.example.laundry.core.model.*
import retrofit2.Response
import retrofit2.http.*

interface LaundryApiService {
    
    // Customer Endpoints
    @POST("customer/orders")
    suspend fun createOrder(@Body orderRequest: CreateOrderRequest): Response<Order>

    @GET("customer/orders")
    suspend fun getCustomerOrders(): Response<List<Order>>

    @GET("customer/shops")
    suspend fun getShops(): Response<List<Shop>>

    @POST("customer/orders/{orderId}/review")
    suspend fun submitReview(@Path("orderId") orderId: String, @Body review: ReviewRequest): Response<Review>

    @POST("customer/orders/{orderId}/dispute")
    suspend fun openDispute(@Path("orderId") orderId: String, @Body dispute: DisputeRequest): Response<Dispute>

    // Vendor Endpoints
    @POST("vendor/register")
    suspend fun registerShop(@Body registration: ShopRegistrationRequest): Response<Shop>

    @GET("vendor/orders")
    suspend fun getVendorOrders(): Response<List<Order>>

    @PATCH("vendor/orders/{orderId}/status")
    suspend fun updateVendorOrderStatus(
        @Path("orderId") orderId: String,
        @Body statusUpdate: StatusUpdateRequest
    ): Response<Order>

    @PUT("vendor/pricing")
    suspend fun updatePricing(@Body services: List<LaundryService>): Response<List<LaundryService>>

    // Driver Endpoints
    @GET("driver/assignments")
    suspend fun getDriverAssignments(): Response<List<Order>>

    @PATCH("driver/orders/{orderId}/status")
    suspend fun updateDriverOrderStatus(
        @Path("orderId") orderId: String,
        @Body statusUpdate: StatusUpdateRequest
    ): Response<Order>
}

data class CreateOrderRequest(
    val vendorId: String,
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val name: String,
    val quantity: Int
)

data class StatusUpdateRequest(
    val status: OrderStatus
)

data class ShopRegistrationRequest(
    val name: String,
    val address: String
)

data class ReviewRequest(
    val rating: Int,
    val comment: String
)

data class DisputeRequest(
    val reason: String
)
