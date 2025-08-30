package com.mehmetmertmazici.domainchecker.model

import com.google.gson.annotations.SerializedName


// Main API Response wrapper
data class ApiResponse(
    @SerializedName("code")
    val code: Int,

    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: ApiMessage?
)

data class ApiMessage(
    @SerializedName("currency")
    val currency: Currency?,

    @SerializedName("domains")
    val domains: List<Domain>?
){
    // ✨ YENİ EKLENEN KISIM:
    // Handle case where message is just a string (error case)
    override fun toString(): String {
        return when {
            domains != null -> "Found ${domains.size} domains"
            else -> "No domains found"
        }
    }
}

data class Currency(
    @SerializedName("id")
    val id: Int,

    @SerializedName("code")
    val code: String,

    @SerializedName("prefix")
    val prefix: String,

    @SerializedName("suffix")
    val suffix: String,

    @SerializedName("format")
    val format: Int,

    @SerializedName("rate")
    val rate: String
)

data class Domain(
    @SerializedName("domain")
    val domain: String,

    @SerializedName("status")
    val status: String, // "available" or "registered"

    @SerializedName("price")
    val price: DomainPrice?
) {

    fun isAvailable(): Boolean = status == "available"

    fun isRegistered(): Boolean = status == "registered"

    fun getTLD(): String {
        return domain.substringAfterLast(".")
    }

    fun getDomainName(): String {
        return domain.substringBeforeLast(".")
    }
}

data class DomainPrice(
    @SerializedName("categories")
    val categories: List<String>?,

    @SerializedName("addons")
    val addons: DomainAddons?,

    @SerializedName("group")
    val group: String?,

    @SerializedName("register")
    val register: Map<String, String>?,

    @SerializedName("transfer")
    val transfer: Map<String, String>?,

    @SerializedName("renew")
    val renew: Map<String, String>?,

    @SerializedName("grace_period")
    val gracePeriod: GracePeriod?,

    @SerializedName("grace_period_days")
    val gracePeriodDays: Int?,

    @SerializedName("grace_period_fee")
    val gracePeriodFee: String?,

    @SerializedName("redemption_period")
    val redemptionPeriod: RedemptionPeriod?,

    @SerializedName("redemption_period_days")
    val redemptionPeriodDays: Int?,

    @SerializedName("redemption_period_fee")
    val redemptionPeriodFee: String?
) {

    fun getRegisterPrice(): String? = register?.get("1")

    fun getRenewalPrice(): String? = renew?.get("1")

    fun getTransferPrice(): String? = transfer?.get("1")

    fun isPopular(): Boolean = categories?.contains("Popular") == true

    fun isHot(): Boolean = group == "hot"
}

data class DomainAddons(
    @SerializedName("dns")
    val dns: Boolean,

    @SerializedName("email")
    val email: Boolean,

    @SerializedName("idprotect")
    val idprotect: Boolean
) {

    fun getAvailableAddons(): List<String> {
        val addons = mutableListOf<String>()
        if (dns) addons.add("DNS")
        if (email) addons.add("Email")
        if (idprotect) addons.add("ID Protection")
        return addons
    }

    fun hasAnyAddon(): Boolean = dns || email || idprotect
}

data class GracePeriod(
    @SerializedName("days")
    val days: Int,

    @SerializedName("price")
    val price: String
)

data class RedemptionPeriod(
    @SerializedName("days")
    val days: Int,

    @SerializedName("price")
    val price: String
)

data class ApiError(
    @SerializedName("error")
    val error: String,

    @SerializedName("code")
    val code: Int,

    @SerializedName("message")
    val message: String?
)