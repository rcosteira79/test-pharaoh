# Expected TEST_PLAN outline — login-happy-path

## com.sample.app.LoginViewModel (tier: unit)

- [ ] submit_validCreds_emitsLoadingThenSuccess
      source: AC-1
- [ ] submit_invalidCreds_emitsError
      source: AC-2
- [ ] submit_networkFailure_emitsError_bannerSemantics
      source: AC-3 + catalog/retrofit.md#network-failure
- [ ] submit_5xx_emitsGenericError
      source: AC-4 + catalog/retrofit.md#5xx
- [ ] stateTransitions_startFromIdle_neverSkipLoading
      source: contract (LoginUiState sealed branches)

## com.sample.auth.DefaultAuthRepository (tier: integration)

- [ ] login_success_returnsTokenFromDataSource
      source: AC-1 + catalog/retrofit.md#success
- [ ] login_4xx_wrapsInFailure
      source: AC-2 + catalog/retrofit.md#4xx
- [ ] login_ioException_wrapsInFailure
      source: AC-3 + catalog/retrofit.md#network-failure
- [ ] login_5xx_wrapsInFailure
      source: AC-4 + catalog/retrofit.md#5xx

## Refactor proposals

(None — sample project has no mock-based tests.)

## Clarification questions

- AC-3 "banner" vs AC-4 "generic error state": same component or different?
