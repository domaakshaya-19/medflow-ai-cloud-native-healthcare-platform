# Patient dashboard: local discovery and profile verification

Use this procedure only when Eureka, Gateway, Auth, and Patient run on the same Windows host. Do not activate the `local` profile in Docker or a multi-host deployment: loopback then points at the wrong machine. Existing default configurations and H2 test databases are unchanged.

## Diagnose before starting or stopping anything

Run from the real Windows host (an isolated executor may reach host HTTP endpoints but not enumerate their processes):

```powershell
Set-Location 'C:\Users\domaa\OneDrive\Desktop\MedFlow-AI'
$listeners = Get-NetTCPConnection -State Listen -LocalPort 8082 -ErrorAction Stop
$listeners | Select-Object LocalAddress, LocalPort, OwningProcess
$listeners.OwningProcess | Sort-Object -Unique | ForEach-Object {
    Get-Process -Id $_ | Select-Object Id, ProcessName, Path
}
```

For a Java owner, inspect its main class/JAR locally in your terminal or IDE; do not paste raw process command lines, which can contain credentials. Check `http://localhost:8082/actuator/health` before launching another Patient instance. A healthy existing instance should be reused. To apply changed configuration, use its owning terminal/IDE to stop and restart that verified instance; never kill all Java processes or choose a PID by guesswork.

Read Eureka without dumping arbitrary metadata:

```powershell
$registry = Invoke-RestMethod 'http://localhost:8761/eureka/apps' -Headers @{Accept='application/json'}
foreach ($application in $registry.applications.application) {
    foreach ($instance in $application.instance) {
        [pscustomobject]@{
            Application = $application.name
            InstanceId = $instance.instanceId
            Host = $instance.hostName
            Ip = $instance.ipAddr
            Port = $instance.port.'$'
            Status = $instance.status
        }
    }
}
```

An instance ID containing the machine hostname is not itself the destination used for routing. Inspect `hostName` and port. The local profile advertises `127.0.0.1` and a deterministic application/port ID, avoiding Windows DNS suffix resolution. Gateway continues to use `lb://patient-service`; no direct service URL is substituted.

After restarting the verified Patient instance with the local profile, wait for the old registration to disappear and for Gateway's registry/load-balancer cache to refresh. Restart only the verified Gateway instance if necessary. Do not unregister an instance that is still running; it will re-register. Eureka self-preservation can retain stale leases, so check every UP instance before testing.

## Environment and configuration precedence

Spring does not automatically import the root `.env` file. Supply process environment variables securely in each service terminal or through the IDE. Do not overwrite `.env`, print environment values, put secrets on command lines, or dump `docker compose config`.

Auth needs `JWT_SECRET`, `AUTH_DB_PASSWORD`, and the intended `AUTH_DB_URL` / `AUTH_DB_USER` if defaults are unsuitable. Patient needs the same `JWT_SECRET`, `PATIENT_DB_PASSWORD`, and the intended `PATIENT_DB_URL` / `PATIENT_DB_USER`. The default Patient database is PostgreSQL `medflow_patient` at localhost:5432, owned by `medflow_patient`. The default Auth database is `medflow_auth` at localhost:5432. The JWT secret must contain at least 32 UTF-8 bytes.

If needed, prompt without echo or shell-history secret literals:

```powershell
$env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'Existing shared JWT secret' -AsSecureString)).Password
# In the Auth terminal only:
$env:AUTH_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Auth database password' -AsSecureString)).Password
# In the Patient terminal only:
$env:PATIENT_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Patient database password' -AsSecureString)).Password
```

Do not create or reset databases as part of diagnosis. The current main configuration uses Hibernate update on startup; use an authorized development database. Never use the H2 test resources as a live-service workaround.

Review override NAMES without exposing their values:

```powershell
Get-ChildItem Env: | Where-Object {
    $_.Name -match '^(EUREKA_|SPRING_PROFILES_|SPRING_CONFIG_|SPRING_APPLICATION_JSON$|SERVER_PORT$|PATIENT_SERVICE_PORT$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$)'
} | Select-Object -ExpandProperty Name
```

Environment properties, JVM options, command-line arguments, and external application files can override packaged YAML. Review their values privately. In particular check `EUREKA_INSTANCE_HOSTNAME`, `EUREKA_INSTANCE_IP_ADDRESS`, `EUREKA_INSTANCE_PREFER_IP_ADDRESS`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`, and active profiles. `EUREKA_SERVER_URL` defaults to http://localhost:8761/eureka. Clear or correct only overrides you have identified as wrong. Do not reset the whole environment.

## Startup order

Verify PostgreSQL first, then reuse or start Eureka, Auth, Patient, and finally Gateway. Open a separate terminal in the repository root for each command; do not run these as a batch and do not start a duplicate listener.

```powershell
pg_isready -h localhost -p 5432
mvn -o -pl backend/discovery-server spring-boot:run '-Dspring-boot.run.arguments=--server.port=8761'
mvn -o -pl backend/auth-service spring-boot:run '-Dspring-boot.run.profiles=local' '-Dspring-boot.run.arguments=--server.port=8081'
mvn -o -pl backend/patient-service spring-boot:run '-Dspring-boot.run.profiles=local' '-Dspring-boot.run.arguments=--server.port=8082'
mvn -o -pl backend/api-gateway spring-boot:run '-Dspring-boot.run.profiles=local' '-Dspring-boot.run.arguments=--server.port=8080'
npm.cmd --prefix frontend run dev -- --host localhost --port 5173 --strictPort
```

A missing offline dependency is a build-tool limitation; do not change dependency versions or ACLs to work around it. Check /actuator/health directly on each port, then verify registry entries before using Gateway. Gateway health alone does not establish downstream readiness.

If the user-level Maven cache produces class-loading errors, the existing workspace
cache can be selected without changing dependency versions. Add the following
quoted argument to the Maven commands above (only if that cache is present):

```powershell
'-Dmaven.repo.local=C:\Users\domaa\OneDrive\Desktop\MedFlow-AI\.m2\repository'
```

## Verify without exposing tokens or creating records

With services ready, the following unauthenticated request should return 401 through both direct Patient and Gateway:

```powershell
foreach ($port in 8082,8080) {
    $r = Invoke-WebRequest "http://localhost:$port/api/patients/me" -SkipHttpErrorCheck
    [pscustomobject]@{Port=$port;Status=[int]$r.StatusCode}
}
```

For authenticated comparison, prompt for an existing patient's credentials. The response token stays in memory, and only status codes are displayed. Use PowerShell 7. Do not enable request tracing or transcripts.

```powershell
$email = Read-Host 'Existing test patient email'
$password = Read-Host 'Patient password' -AsSecureString
$loginBody = @{email=$email; password=[System.Net.NetworkCredential]::new('', $password).Password} | ConvertTo-Json
try {
    $session = Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
    foreach ($port in 8082,8080) {
        $r = Invoke-WebRequest "http://localhost:$port/api/patients/me" -Headers @{Authorization="Bearer $($session.accessToken)"} -SkipHttpErrorCheck
        [pscustomobject]@{Port=$port;Status=[int]$r.StatusCode}
    }
} finally {
    Remove-Variable session,loginBody,password -ErrorAction SilentlyContinue
}
```

Expected: 200 for an existing profile; 404 for a genuine missing profile; 401 for missing, malformed, expired, or wrong-signature tokens; 403 for a valid non-patient token on /me. Another patient's profile remains forbidden. A Gateway 503 means no usable discovered instance; a DNS/connect failure is transport-level and can surface as 500/502. A direct Patient 500 with the same valid token needs sanitized Patient-side diagnostics; do not change missing-profile semantics to disguise it.

Frontend check: open http://localhost:5173, log in, and inspect only request URL/status (do not copy the token). An existing profile opens the dashboard; a 404 goes to /patient/setup; other service errors remain visible with retry. Check that frontend VITE_API_URL targets http://localhost:8080 and restart Vite after changing its environment. Do not create a fake profile or turn all server errors into onboarding.

## Automated checks

```powershell
$agent = '-DargLine=-javaagent:C:\Users\domaa\.m2\repository\net\bytebuddy\byte-buddy-agent\1.15.11\byte-buddy-agent-1.15.11.jar'
mvn -o --fail-at-end -pl backend/api-gateway,backend/auth-service,backend/patient-service test $agent
```

Gateway tests use an HTTP stub with a loopback discovery supplier to verify path/header forwarding and status propagation; they are not proof of live Eureka or PostgreSQL. Patient/Auth tests use their existing isolated H2 configuration. No target deletion or database reset is needed.

The Gateway socket tests require local loopback socket access. In a restricted
executor, run them from the normal host terminal instead. The successful Gateway
test command in this investigation used the existing workspace cache and agent:

```powershell
mvn -o -pl backend/api-gateway test '-Dmaven.repo.local=C:\Users\domaa\OneDrive\Desktop\MedFlow-AI\.m2\repository' '-DargLine=-javaagent:C:\Users\domaa\OneDrive\Desktop\MedFlow-AI\.m2\repository\net\bytebuddy\byte-buddy-agent\1.15.11\byte-buddy-agent-1.15.11.jar'
```
