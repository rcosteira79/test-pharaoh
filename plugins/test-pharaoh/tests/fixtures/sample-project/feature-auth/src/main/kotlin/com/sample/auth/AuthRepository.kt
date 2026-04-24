package com.sample.auth

import javax.inject.Inject

sealed class LoginError {
    data object InvalidCredentials : LoginError()

    data object Network : LoginError()

    data class Server(
        val code: Int,
    ) : LoginError()
}

interface AuthRepository {
    suspend fun login(
        email: String,
        password: String,
    ): Result<String>
}

class DefaultAuthRepository
    @Inject
    constructor(
        private val dataSource: AuthDataSource,
    ) : AuthRepository {
        override suspend fun login(
            email: String,
            password: String,
        ): Result<String> {
            // Body intentionally present so the extractor has something to strip.
            return runCatching { dataSource.login(LoginRequest(email, password)).token }
        }
    }
