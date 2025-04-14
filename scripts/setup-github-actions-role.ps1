# GitHub Actions Role Setup Script for AWS
# PowerShell version for Windows users

param (
    [string]$Profile = "github-actions",
    [string]$Region = "us-east-1",
    [Parameter(Mandatory=$true)][string]$Account,
    [switch]$Help
)

function Show-Help {
    Write-Host "Usage: .\setup-github-actions-role.ps1 -Account <AWS_ACCOUNT_ID> [-Profile <PROFILE_NAME>] [-Region <AWS_REGION>]"
    Write-Host "Creates AWS profile and GitHub Actions role for ECR access"
    Write-Host ""
    Write-Host "Parameters:"
    Write-Host "  -Profile <NAME>    AWS profile name (default: github-actions)"
    Write-Host "  -Region <REGION>   AWS region (default: us-east-1)"
    Write-Host "  -Account <ID>      AWS account ID (required)"
    Write-Host "  -Help              Show this help message"
    exit
}

if ($Help) {
    Show-Help
}

# Display setup information
Write-Host "=== Setting up GitHub Actions deployment for ECR ==="
Write-Host "AWS Profile: $Profile"
Write-Host "AWS Region: $Region"
Write-Host "AWS Account ID: $Account"
Write-Host ""

# Prompt for AWS credentials
Write-Host "Please enter AWS credentials for the profile:"
$AccessKeyId = Read-Host -Prompt "AWS Access Key ID"
$SecretKey = Read-Host -Prompt "AWS Secret Access Key" -AsSecureString
$SecretKeyPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecretKey))

# Create or update AWS profile
Write-Host "Creating/updating AWS profile..."
aws configure set aws_access_key_id "$AccessKeyId" --profile "$Profile"
aws configure set aws_secret_access_key "$SecretKeyPlain" --profile "$Profile"
aws configure set region "$Region" --profile "$Profile"
aws configure set output "json" --profile "$Profile"

# Clear the plain text secret from memory
$SecretKeyPlain = $null
[System.GC]::Collect()

Write-Host "AWS profile '$Profile' created/updated successfully."

# Create trust policy document with the correct account ID
Write-Host "Creating trust policy for GitHub Actions..."
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$templatePath = Join-Path $scriptDir "github-actions-trust-policy.json"
$trustPolicyPath = Join-Path $scriptDir "github-actions-trust-policy-temp.json"

# Read the template and replace the account ID
$templateContent = Get-Content -Path $templatePath -Raw
$trustPolicy = $templateContent -replace "ACCOUNT_ID", $Account
Set-Content -Path $trustPolicyPath -Value $trustPolicy -NoNewline

# Check if the OIDC provider exists
Write-Host "Checking if OIDC provider exists..."
try {
    $oidcProviderExists = aws iam get-open-id-connect-provider --open-id-connect-provider-arn "arn:aws:iam::$Account:oidc-provider/token.actions.githubusercontent.com" --profile "$Profile" 2>$null
} catch {
    Write-Host "OIDC provider does not exist. Creating..."
    try {
        aws iam create-open-id-connect-provider --url "https://token.actions.githubusercontent.com" --client-id-list "sts.amazonaws.com" --thumbprint-list "6938fd4d98bab03faadb97b34396831e3780aea1" --profile "$Profile"
        Write-Host "OIDC provider created successfully."
    } catch {
        Write-Host "Failed to create OIDC provider. You may need to create it manually in the AWS console."
    }
}

# Check if role exists
Write-Host "Checking if role already exists..."
try {
    $roleExists = aws iam get-role --role-name github-actions-role --profile "$Profile" 2>$null
    Write-Host "Role 'github-actions-role' already exists, updating trust policy..."
    aws iam update-assume-role-policy --role-name github-actions-role --policy-document "fileb://$trustPolicyPath" --profile "$Profile"
} catch {
    # Create role
    Write-Host "Creating GitHub Actions role..."
    aws iam create-role --role-name github-actions-role --assume-role-policy-document "fileb://$trustPolicyPath" --profile "$Profile"

    # Attach AdministratorAccess policy
    Write-Host "Attaching AdministratorAccess policy..."
    aws iam attach-role-policy --role-name github-actions-role --policy-arn arn:aws:iam::aws:policy/AdministratorAccess --profile "$Profile"
}

# Create ECR repositories
$createRepos = Read-Host -Prompt "Do you want to create ECR repositories now? (y/n)"
if ($createRepos -eq "y" -or $createRepos -eq "Y") {
    $services = Read-Host -Prompt "Enter comma-separated service names (e.g., hello-service,auth-service)"
    
    $serviceArray = $services -split ','
    foreach ($service in $serviceArray) {
        $service = $service.Trim()
        $repoName = "sbxservice-$service"
        
        # Check if repository exists
        try {
            $repoExists = aws ecr describe-repositories --repository-names "$repoName" --profile "$Profile" 2>$null
            Write-Host "ECR repository '$repoName' already exists, skipping creation."
        } catch {
            Write-Host "Creating ECR repository: $repoName"
            aws ecr create-repository --repository-name "$repoName" --image-scanning-configuration scanOnPush=true --profile "$Profile"
        }
    }
}

# Clean up
Remove-Item -Path $trustPolicyPath -Force

Write-Host ""
Write-Host "=== Setup Complete ==="
Write-Host "GitHub Actions role 'github-actions-role' has been created with AdminAccess"
Write-Host "You can now use this role in your GitHub Actions workflows"
Write-Host "AWS Account ID to use in workflow: $Account" 