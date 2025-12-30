# EC2 Deployment Guide for Acedia Project

## Prerequisites on EC2 Instance
1. Java 21 installed
2. PostgreSQL database accessible (RDS or local)
3. Chrome/Chromium installed (for Selenium web scraping)

## Required Environment Variables

The `prod` profile uses the following environment variables with defaults:

### Database Configuration (Required)
```bash
export RDS_HOSTNAME=your-database-host.rds.amazonaws.com  # Default: acedia.ce7sussmcfe2.us-east-1.rds.amazonaws.com
export RDS_PORT=5432                                       # Default: 5432
export RDS_DB_NAME=acedia                                  # Default: acedia
export RDS_USERNAME=postgres                               # Default: postgres
export RDS_PASSWORD=your-secure-password                   # Default: postgres1234 (CHANGE THIS!)
```

### Application Profile
```bash
export SPRING_PROFILES_ACTIVE=prod
```

## Deployment Steps

### 1. Upload JAR file to EC2
```bash
# From your local machine
scp -i your-key.pem target/acedia-0.0.1-SNAPSHOT.jar ec2-user@your-ec2-ip:/home/ec2-user/
```

### 2. SSH into EC2
```bash
ssh -i your-key.pem ec2-user@your-ec2-ip
```

### 3. Set Environment Variables
Create a file `/home/ec2-user/acedia-env.sh`:
```bash
#!/bin/bash
export SPRING_PROFILES_ACTIVE=prod
export RDS_HOSTNAME=your-database-host
export RDS_PORT=5432
export RDS_DB_NAME=acedia
export RDS_USERNAME=postgres
export RDS_PASSWORD=your-secure-password
```

Make it executable:
```bash
chmod +x acedia-env.sh
```

### 4. Create Required Directories
```bash
sudo mkdir -p /var/app/acedia-storage
sudo mkdir -p /var/app/scraped-novels
sudo chown -R ec2-user:ec2-user /var/app
```

### 5. Run the Application

#### Option A: Run in foreground (for testing)
```bash
source acedia-env.sh
java -jar acedia-0.0.1-SNAPSHOT.jar
```

#### Option B: Run as background service
```bash
source acedia-env.sh
nohup java -jar acedia-0.0.1-SNAPSHOT.jar > acedia.log 2>&1 &
```

#### Option C: Create a systemd service (recommended for production)
Create `/etc/systemd/system/acedia.service`:
```ini
[Unit]
Description=Acedia Novel Scraper Service
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="RDS_HOSTNAME=your-database-host"
Environment="RDS_PORT=5432"
Environment="RDS_DB_NAME=acedia"
Environment="RDS_USERNAME=postgres"
Environment="RDS_PASSWORD=your-secure-password"
ExecStart=/usr/bin/java -jar /home/ec2-user/acedia-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Then start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl start acedia
sudo systemctl enable acedia  # Auto-start on boot
sudo systemctl status acedia  # Check status
```

View logs:
```bash
sudo journalctl -u acedia -f
```

## Application Configuration (prod profile)

- **Port**: 5000 (configurable via `SERVER_PORT` env var if needed)
- **Storage**: Local filesystem at `/var/app/acedia-storage`
- **Scraped novels**: Saved to `/var/app/scraped-novels`
- **Health check**: `http://your-ec2-ip:5000/actuator/health`

## Security Group Settings
Make sure your EC2 security group allows:
- Inbound TCP on port 5000 (application)
- Outbound to PostgreSQL (port 5432)
- Outbound HTTP/HTTPS (for web scraping)

## Quick Start Command (All-in-One)
```bash
# Set variables and run
export SPRING_PROFILES_ACTIVE=prod \
       RDS_HOSTNAME=your-db-host \
       RDS_PORT=5432 \
       RDS_DB_NAME=acedia \
       RDS_USERNAME=postgres \
       RDS_PASSWORD=your-password && \
java -jar acedia-0.0.1-SNAPSHOT.jar
```

## Verification
Once running, check:
```bash
curl http://localhost:5000/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    ...
  }
}
```

## Troubleshooting

### Check if app is running
```bash
ps aux | grep acedia
netstat -tlnp | grep 5000
```

### View logs (if using systemd)
```bash
sudo journalctl -u acedia -n 100 --no-pager
```

### View logs (if using nohup)
```bash
tail -f acedia.log
```

### Common Issues
1. **Database connection failed**: Check RDS security group allows EC2 instance
2. **Port 5000 already in use**: Kill existing process or change port
3. **Permission denied on /var/app**: Run `sudo chown -R ec2-user:ec2-user /var/app`
