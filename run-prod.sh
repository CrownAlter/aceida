#!/bin/bash
# Quick script to run Acedia with prod profile
# Usage: ./run-prod.sh

# Set environment variables
export SPRING_PROFILES_ACTIVE=prod

# Database configuration - UPDATE THESE VALUES
export RDS_HOSTNAME=${RDS_HOSTNAME:-"your-database-host.rds.amazonaws.com"}
export RDS_PORT=${RDS_PORT:-5432}
export RDS_DB_NAME=${RDS_DB_NAME:-"acedia"}
export RDS_USERNAME=${RDS_USERNAME:-"postgres"}
export RDS_PASSWORD=${RDS_PASSWORD:-"o"}

# Create required directories
mkdir -p /var/app/acedia-storage 2>/dev/null || sudo mkdir -p /var/app/acedia-storage
mkdir -p /var/app/scraped-novels 2>/dev/null || sudo mkdir -p /var/app/scraped-novels

echo "Starting Acedia with prod profile..."
echo "Database: ${RDS_USERNAME}@${RDS_HOSTNAME}:${RDS_PORT}/${RDS_DB_NAME}"
echo "Application will run on port 5000"
echo ""

# Run the application
java -jar acedia-0.0.1-SNAPSHOT.jar
