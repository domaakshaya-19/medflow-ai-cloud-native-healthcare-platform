# Day-1 architecture

The React client calls only the API Gateway. The gateway discovers and forwards `/api/auth/**` to Auth Service. Auth Service owns `medflow_auth`; it registers with Eureka. No future clinical service is implemented.

```text
React :5173 -> Gateway :8080 -> Auth Service :8081 -> PostgreSQL :5432
                         |             |
                         +---- Eureka :8761
```
