package com.example.laundry.core.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a laundry order in the system.
 *
 * @property id Unique identifier for the order.
 * @property status Current progress of the order (e.g., [OrderStatus.REQUESTED]).
 * @property customerId The ID of the customer who placed the order.
 * @property vendorId The ID of the shop handling the laundry, if assigned.
 * @property driverId The ID of the driver assigned for pickup/delivery, if assigned.
 * @property totalCents Total cost of the order in cents to avoid floating point issues.
 * @property items The list of laundry items included in this order.
 */
data class Order(
    @SerializedName("orderId") val id: String,
    @SerializedName("status") val status: OrderStatus,
    @SerializedName("customerId") val customerId: String,
    @SerializedName("vendorId") val vendorId: String?,
    @SerializedName("driverId") val driverId: String?,
    @SerializedName("totalCents") val totalCents: Int,
    @SerializedName("items") val items: List<OrderItem> = emptyList()
)

/**
 * Possible states for an [Order].
 */
enum class OrderStatus {
    /** Order placed by customer but not yet accepted. */
    @SerializedName("requested") REQUESTED,
    /** Alternative for requested. */
    @SerializedName("queued") QUEUED,
    /** Order accepted by a vendor. */
    @SerializedName("accepted") ACCEPTED,
    /** A driver has been assigned to pick up the laundry. */
    @SerializedName("pickup_assigned") PICKUP_ASSIGNED,
    /** Driver has picked up the laundry from the customer. */
    @SerializedName("picked_up") PICKED_UP,
    /** Laundry has arrived at the vendor's shop. */
    @SerializedName("at_vendor") AT_VENDOR,
    /** Vendor is currently cleaning the items. */
    @SerializedName("processing") PROCESSING,
    /** Cleaning is complete and ready to be delivered back. */
    @SerializedName("ready_for_delivery") READY_FOR_DELIVERY,
    /** A driver has been assigned for the final delivery. */
    @SerializedName("delivery_assigned") DELIVERY_ASSIGNED,
    /** Driver is on the way to the customer. */
    @SerializedName("out_for_delivery") OUT_FOR_DELIVERY,
    /** Order successfully delivered to the customer. */
    @SerializedName("delivered") DELIVERED,
    /** Order was cancelled by the customer or vendor. */
    @SerializedName("cancelled") CANCELLED
}

/**
 * Details of a specific item within an [Order].
 *
 * @property name The name of the item (e.g., "Silk Shirt").
 * @property quantity Number of units.
 * @property priceCents Price per unit in cents.
 */
data class OrderItem(
    val name: String,
    val quantity: Int,
    val priceCents: Int
)

/**
 * Represents a laundry shop participating in the marketplace.
 *
 * @property id Unique identifier for the shop.
 * @property name Display name of the shop.
 * @property address Physical location of the shop.
 * @property rating Average customer rating (0.0 to 5.0).
 * @property services List of services (e.g., "Wash & Fold") offered by this shop.
 */
data class Shop(
    @SerializedName("shopId") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("rating") val rating: Float,
    @SerializedName("services") val services: List<LaundryService> = emptyList(),
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0
)

fun distanceBetweenKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusKm * c
}

fun trackingStatusLabel(status: OrderStatus?): String = when (status) {
    OrderStatus.REQUESTED, OrderStatus.QUEUED -> "Order received"
    OrderStatus.ACCEPTED -> "Vendor accepted your order"
    OrderStatus.PICKUP_ASSIGNED -> "Driver is en route to pickup"
    OrderStatus.PICKED_UP -> "Laundry collected"
    OrderStatus.AT_VENDOR -> "Laundry is at the shop"
    OrderStatus.PROCESSING -> "Laundry is being cleaned"
    OrderStatus.READY_FOR_DELIVERY -> "Ready for delivery"
    OrderStatus.DELIVERY_ASSIGNED -> "Delivery rider assigned"
    OrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    OrderStatus.DELIVERED -> "Delivered"
    OrderStatus.CANCELLED -> "Cancelled"
    null -> "Updating status..."
}

/**
 * Defines a specific type of service offered by a [Shop].
 *
 * @property id Unique identifier for the service type.
 * @property name Name of the service.
 * @property priceCents Cost per unit in cents.
 * @property unit The unit of measurement (e.g., "kg", "piece").
 */
data class LaundryService(
    val id: String,
    val name: String,
    val priceCents: Int,
    val unit: String // e.g., "kg", "piece"
)

/**
 * Customer feedback for a completed [Order].
 *
 * @property id Unique identifier for the review.
 * @property orderId The ID of the order being reviewed.
 * @property rating Numeric score (1-5).
 * @property comment Textual feedback from the customer.
 * @property authorName Name of the person who wrote the review.
 */
data class Review(
    val id: String,
    val orderId: String,
    val rating: Int,
    val comment: String,
    val authorName: String
)

/**
 * A formal complaint or issue raised regarding an [Order].
 *
 * @property id Unique identifier for the dispute.
 * @property orderId The ID of the order in question.
 * @property reason Detailed explanation for the dispute.
 * @property status Current resolution state of the dispute.
 * @property createdAt Timestamp when the dispute was created.
 */
data class Dispute(
    val id: String,
    val orderId: String,
    val reason: String,
    val status: DisputeStatus,
    val createdAt: String
)

/**
 * Possible resolution states for a [Dispute].
 */
enum class DisputeStatus {
    /** Dispute has been opened but not yet processed. */
    OPEN,
    /** Support team is investigating the claim. */
    UNDER_REVIEW,
    /** Issue has been settled. */
    RESOLVED,
    /** Claim was found to be invalid. */
    REJECTED
}
