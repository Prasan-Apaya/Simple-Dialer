package com.simplemobiletools.dialer.network.model


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SpamUser(
    @Json(name = "phoneNumber")
    val phoneNumber: String,
    @Json(name = "telecomProvider")
    val telecomProvider: String,
    @Json(name = "telemarketer")
    val telemarketer: String,
    @Json(name = "username")
    val username: String
)
