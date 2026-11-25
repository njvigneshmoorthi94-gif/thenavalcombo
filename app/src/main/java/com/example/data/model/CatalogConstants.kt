package com.example.data.model

object CatalogConstants {
    val MOBILE_BRANDS = listOf(
        "Thennaval",
        "Vivo",
        "Oppo",
        "Samsung",
        "Redmi",
        "Realme",
        "iPhone",
        "Other"
    )

    val PART_TYPES = listOf(
        "Display",
        "Battery",
        "Touch",
        "Charging Connector",
        "Speaker",
        "Camera",
        "Back Panel",
        "Other"
    )

    val STOCK_CATEGORIES = listOf(
        "Grocery",
        "General",
        "Display & Touch",
        "Power & Battery",
        "Charging & Ports",
        "Audio & Mic",
        "Camera & Optics",
        "Body & Housing",
        "Accessories",
        "Other"
    )

    val PAYMENT_METHODS = listOf(
        "Cash",
        "UPI",
        "Bank Transfer",
        "Other"
    )

    val ORDER_STATUSES = listOf(
        "Completed",
        "Unpaid",
        "Partial Payment",
        "Returned"
    )

    val RETURN_STATUSES = listOf(
        "Processed",
        "Pending",
        "Rejected"
    )
}
