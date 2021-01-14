package com.simplemobiletools.dialer.network

import com.simplemobiletools.dialer.helpers.GET_SPAM_USERS
import com.simplemobiletools.dialer.network.model.SpamResponseList

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

interface SpamUserService {
    @GET(GET_SPAM_USERS)
    @Headers("Content-Type: application/json")
    suspend fun getSpamUsers(): Response<SpamResponseList>
}
