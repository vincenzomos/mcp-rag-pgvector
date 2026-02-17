@echo off
echo === PostgreSQL Database Reset Script ===
echo This script will:
echo 1. Stop the existing PostgreSQL container
echo 2. Remove the container and its volumes
echo 3. Start a fresh container with init.sql
echo.

REM Stop and remove existing container
echo Stopping existing container...
docker-compose -f docker-compose-postgres.yml down -v

echo Removing any orphaned volumes...
docker volume prune -f

REM Start fresh container
echo Starting fresh PostgreSQL container...
docker-compose -f docker-compose-postgres.yml up -d

echo Waiting for database to be ready...
timeout /t 10 /nobreak > nul

REM Check if database is ready
echo Testing database connection...
docker exec mcp-pgvector pg_isready -U postgres -d mcp_uren_db

if %ERRORLEVEL% == 0 (
    echo ✓ Database is ready!
    echo ✓ init.sql should have been executed
    echo.
    echo You can now start your Spring Boot application.
) else (
    echo ✗ Database is not ready yet. Please wait a moment and try again.
)

echo.
echo To check database logs: docker logs mcp-pgvector
echo To connect to database: docker exec -it mcp-pgvector psql -U postgres -d mcp_uren_db

pause
