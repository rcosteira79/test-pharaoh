# As a user I want to log in so that I can access my account

## Acceptance criteria

- **AC-1**: On submitting valid credentials, I see the home screen within 3 seconds.
- **AC-2**: On invalid credentials, I see an inline error "Wrong email or password" and the password field is cleared.
- **AC-3**: On network failure during login, I see a banner "Network issue — please retry" and the submit button remains enabled.
- **AC-4**: On a 5xx backend response, I see a generic error state; retrying is idempotent.
