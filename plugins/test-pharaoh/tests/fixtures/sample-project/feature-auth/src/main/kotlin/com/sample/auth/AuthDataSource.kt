package com.sample.auth

import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
)

interface AuthDataSource {
    @POST("/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): LoginResponse
}
