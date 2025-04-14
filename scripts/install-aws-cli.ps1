# AWS CLI Installation Script for Windows
# This script downloads and installs AWS CLI v2

Write-Host "=== AWS CLI Installation Script ==="
Write-Host "This script will download and install AWS CLI v2 on your Windows system."
Write-Host ""

# Create temporary directory
$tempDir = "$env:TEMP\aws-cli-installer"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$installerPath = "$tempDir\AWSCLIV2.msi"

# Download AWS CLI installer
Write-Host "Downloading AWS CLI v2 installer..."
try {
    Invoke-WebRequest -Uri "https://awscli.amazonaws.com/AWSCLIV2.msi" -OutFile $installerPath
    Write-Host "Download completed successfully."
} catch {
    Write-Host "Failed to download AWS CLI installer: $_" -ForegroundColor Red
    Write-Host "Please download manually from: https://awscli.amazonaws.com/AWSCLIV2.msi"
    exit 1
}

# Install AWS CLI
Write-Host "Installing AWS CLI v2..."
try {
    Start-Process msiexec.exe -Wait -ArgumentList "/i $installerPath /quiet /norestart"
    Write-Host "AWS CLI installed successfully." -ForegroundColor Green
} catch {
    Write-Host "Failed to install AWS CLI: $_" -ForegroundColor Red
    exit 1
}

# Clean up
Remove-Item -Path $tempDir -Recurse -Force

# Verify installation
Write-Host "Verifying installation..."
try {
    # Refresh environment path without requiring restart
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    
    $awsVersion = aws --version
    Write-Host "AWS CLI installation verified: $awsVersion" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Configure AWS CLI with your credentials:"
    Write-Host "   aws configure"
    Write-Host "2. Or run the GitHub Actions setup script:"
    Write-Host "   .\scripts\setup-github-actions-role.ps1 -Account YOUR_AWS_ACCOUNT_ID"
} catch {
    Write-Host "AWS CLI is installed but not available in the current session." -ForegroundColor Yellow
    Write-Host "Please restart your PowerShell session or computer, then verify installation with:" -ForegroundColor Yellow
    Write-Host "aws --version"
    Write-Host ""
    Write-Host "After restarting, you can proceed with AWS CLI configuration."
}

Write-Host ""
Write-Host "=== Installation Complete ===" 