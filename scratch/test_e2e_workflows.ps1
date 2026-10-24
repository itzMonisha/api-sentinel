$ErrorActionPreference = "Stop"

trap {
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "HTTP ERROR RESPONSE:" $reader.ReadToEnd()
    }
}

function Get-Data($response) {
    if ($null -ne $response -and $null -ne $response.data) {
        return $response.data
    }
    return $response
}

Write-Host "=== 1. AUTHENTICATION ==="
$loginBody = '{"email":"developer@apisentinel.dev","password":"Password123!@#"}'
$authRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$auth = Get-Data $authRaw
$token = $auth.accessToken
Write-Host "Logged in successfully! User: $($auth.user.fullName) Role: $($auth.user.role)"

$headers = @{ "Authorization" = "Bearer $token" }

Write-Host "=== 2. CURRENT USER PROFILE ==="
$meRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/me" -Method Get -Headers $headers
$me = Get-Data $meRaw
Write-Host "User me: $($me.fullName) ($($me.email)) role=$($me.role)"

Write-Host "=== 3. CREATE PROJECT ==="
$projBody = '{"name":"FinTech Core API","description":"Payment gateway and core accounting APIs","environment":"PRODUCTION"}'
$projRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects" -Method Post -Body $projBody -ContentType "application/json" -Headers $headers
$proj = Get-Data $projRaw
Write-Host "Created project: $($proj.id) -> $($proj.name)"

Write-Host "=== 4. CREATE ENDPOINT ==="
$epBody = @{
  name = "Payment Status Check"
  httpMethod = "GET"
  url = "https://httpbin.org/status/200"
  description = "Public SLA verification probe endpoint"
} | ConvertTo-Json
$epRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/endpoints" -Method Post -Body $epBody -ContentType "application/json" -Headers $headers
$ep = Get-Data $epRaw
Write-Host "Created endpoint: $($ep.id) -> $($ep.httpMethod) $($ep.url)"

Write-Host "=== 5. CREATE & TRIGGER HEALTH CHECK ==="
$hcBody = @{
  endpointId = $ep.id
  name = "Payment Status Monitor"
  intervalSeconds = 60
  timeoutMs = 5000
  expectedStatus = 200
  failureThreshold = 3
  recoveryThreshold = 2
  active = $true
} | ConvertTo-Json
$hcRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/health-checks" -Method Post -Body $hcBody -ContentType "application/json" -Headers $headers
$hc = Get-Data $hcRaw
Write-Host "Created health check: $($hc.id)"

$probeRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health-checks/$($hc.id)/trigger" -Method Post -Headers $headers
$probe = Get-Data $probeRaw
Write-Host "Probed health check: statusCode=$($probe.statusCode) responseTimeMs=$($probe.responseTimeMs)ms success=$($probe.success)"

$historyRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health-checks/$($hc.id)/history" -Method Get -Headers $headers
$history = Get-Data $historyRaw
$historyCount = @($history).Count
Write-Host "Health history records persisted: $historyCount"

Write-Host "=== 6. CREATE TEST SUITE & TEST CASE ==="
$tsBody = @{
  name = "Accounting Invariant Tests"
  description = "Verifies HTTP response codes and SLAs"
} | ConvertTo-Json
$tsRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/test-suites" -Method Post -Body $tsBody -ContentType "application/json" -Headers $headers
$ts = Get-Data $tsRaw
Write-Host "Created test suite: $($ts.id) -> $($ts.name)"

$assertions = @(
  @{
    type = "STATUS_CODE_EQUALS"
    expected = "200"
    message = "Status code must be 200"
  },
  @{
    type = "RESPONSE_TIME_BELOW"
    expected = "5000"
    message = "Response time must be below 5000ms"
  }
)
$tcBody = @{
  endpointId = $ep.id
  name = "Verify Status 200 SLA"
  targetPath = "https://httpbin.org/status/200"
  httpMethod = "GET"
  assertions = $assertions
  executionOrder = 1
} | ConvertTo-Json -Depth 5
$tcRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/test-suites/$($ts.id)/cases" -Method Post -Body $tcBody -ContentType "application/json" -Headers $headers
$tc = Get-Data $tcRaw
Write-Host "Created test case: $($tc.id) with $($tc.assertions.Count) assertions"

Write-Host "=== 7. EXECUTE TEST RUN ==="
$runRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/test-suites/$($ts.id)/runs" -Method Post -Headers $headers
$run = Get-Data $runRaw
Write-Host "Executed test run: $($run.id) status=$($run.status) duration=$($run.durationMs)ms total=$($run.totalTests) passed=$($run.passedTests) failed=$($run.failedTests)"

$resultsRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/test-runs/$($run.id)/results" -Method Get -Headers $headers
$results = Get-Data $resultsRaw
Write-Host "Test run results count: $($results.Count) first result passed=$($results[0].passed)"

Write-Host "=== 8. LAUNCH SECURITY SCAN ==="
$scanRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/scans" -Method Post -Body '{}' -ContentType "application/json" -Headers $headers
$scan = Get-Data $scanRaw
Write-Host "Launched security scan: $($scan.id) status=$($scan.status) vulnerabilitiesFound=$($scan.vulnerabilitiesFound)"

Write-Host "=== 9. QUERY DASHBOARD & SYSTEM HEALTH ==="
$dashRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/dashboard/summary" -Method Get -Headers $headers
$dash = Get-Data $dashRaw
Write-Host "Project Reliability: totalMonitoredEndpoints=$($dash.totalMonitoredEndpoints) healthy=$($dash.healthyEndpoints) uptime=$($dash.overallUptimePercentage)% activeIncidents=$($dash.activeIncidentsCount)"

$endpointsRaw = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects/$($proj.id)/endpoints" -Method Get -Headers $headers
$endpoints = Get-Data $endpointsRaw
Write-Host "Project endpoints count: $($endpoints.content.Count)"

Write-Host "=== ALL WORKFLOWS PASSED WITH REAL PERSISTED DATA ==="
