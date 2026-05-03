package com.example.laundry.core.model

import com.google.gson.annotations.SerializedName

data class Order(
    @SerializedName("orderId") val id: String,
    @SerializedName("status") val status: OrderStatus,
    @SerializedName("customerId") val customerId: String,
    @SerializedName("vendorId") val vendorId: String?,
    @SerializedName("driverId") val driverId: String?,
    @SerializedName("totalCents") val totalCents: Int,
    @SerializedName("items") val items: List<OrderItem> = emptyList()
)

enum class OrderStatus {
    @SerializedName("requested") REQUESTED,
    @SerializedName("accepted") ACCEPTED,
    @SerializedName("pickup_assigned") PICKUP_ASSIGNED,
    @SerializedName("picked_up") PICKED_UP,
    @SerializedName("at_vendor") AT_VENDOR,
    @SerializedName("processing") PROCESSING,
    @SerializedName("ready_for_delivery") READY_FOR_DELIVERY,
    @SerializedName("delivery_assigned") DELIVERY_ASSIGNED,
    @SerializedName("out_for_delivery") OUT_FOR_DELIVERY,
    @SerializedName("delivered") DELIVERED,
    @SerializedName("cancelled") CANCELLED
}

data class OrderItem(
    val name: String,
    val quantity: Int,
    val priceCents: Int
)

data class Shop(
    @SerializedName("shopId") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("rating") val rating: Float,
    @SerializedName("services") val services: List<LaundryService> = emptyList()
)

data class LaundryService(
    val id: String,
    val name: String,
    val priceCents: Int,
    val unit: String // e.g., "kg", "piece"
)

data class Review(
    val id: String,
    val orderId: String,
    val rating: Int,
    val comment: String,
    val authorName: String
)

data class Dispute(
    val id: String,
    val orderId: String,
    val reason: String,
    val status: DisputeStatus,
    val createdAt: String
)

enum class DisputeStatus {
    OPEN, UNDER_REVIEW, RESOLVED, REJECTED
}
