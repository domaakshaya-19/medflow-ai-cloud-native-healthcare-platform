# Day-1 security

Passwords are BCrypt-hashed at cost 12. Public registration has no role input and always assigns PATIENT. Emails are trimmed and lowercased. JWTs use an environment-provided HMAC secret of at least 32 bytes and expire after the configured duration. The API is stateless, `/me` is server-protected, and API DTOs never include password hashes.
