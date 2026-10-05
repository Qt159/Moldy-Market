# Start Postgres if not running
$container = docker inspect moldy-market-postgres --format "{{.State.Status}}" 2>$null
if ($container -ne "running") {
    Write-Host "Starting Postgres..." -ForegroundColor Yellow
    $env:POSTGRES_PASSWORD="moldy_password"
    docker compose up postgres -d
    Start-Sleep -Seconds 5
}

# Run Spring Boot
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh"
