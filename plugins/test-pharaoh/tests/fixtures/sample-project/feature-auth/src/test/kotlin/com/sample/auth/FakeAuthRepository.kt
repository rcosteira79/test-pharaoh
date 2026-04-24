package com.sample.auth

class FakeAuthRepository(
    private var response: Result<String> = Result.success("token"),
) : AuthRepository {
    fun setResponse(result: Result<String>) {
        response = result
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Result<String> = response
}
