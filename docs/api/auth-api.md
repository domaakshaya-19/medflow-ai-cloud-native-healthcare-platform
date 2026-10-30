# Authentication API

| Method | Path | Access | Result |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Creates a PATIENT account (201) |
| POST | `/api/auth/login` | Public | Returns a signed bearer token and user |
| GET | `/api/auth/me` | Bearer JWT | Returns the authenticated user |

Errors share `timestamp`, `status`, `error`, `message`, `path`, and optional `fieldErrors`.
