# Load local-only variables without executing the file or printing its values.
$envFile = Join-Path $PSScriptRoot ".env.local"
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$requiredLocalVariables = @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD")
$missingLocalVariables = @($requiredLocalVariables | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "Process"))
})
if ($missingLocalVariables.Count -gt 0) {
    throw "Set these required local variables in .env.local or the process environment: $($missingLocalVariables -join ', ')"
}

# Start Postgres if not running
$container = docker inspect moldy-market-postgres --format "{{.State.Status}}" 2>$null
if ($container -ne "running") {
    Write-Host "Starting Postgres..." -ForegroundColor Yellow
    if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
        $env:POSTGRES_PASSWORD=$env:DB_PASSWORD
    }
    docker compose up postgres -d
    Start-Sleep -Seconds 5
}

# Run Spring Boot
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh"
