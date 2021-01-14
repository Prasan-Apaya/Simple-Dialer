package com.simplemobiletools.dialer.network.model


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class SpamResponseList(
    @Json(name = "spam_users")
    val spamUsers: List<SpamUser>
)
