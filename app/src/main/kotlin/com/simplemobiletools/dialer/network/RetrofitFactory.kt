package com.simplemobiletools.dialer.network

import com.simplemobiletools.dialer.helpers.BASE_URL
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitFactory {


    private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()


    private fun retrofit() : Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()


    val spamUserService : SpamUserService = retrofit().create(SpamUserService::class.java)

}
