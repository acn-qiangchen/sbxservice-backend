# AWS and GitHub Actions Setup Scripts

This directory contains scripts to help set up AWS CLI and configure GitHub Actions for ECR deployments.

## Prerequisites

- Windows, macOS, or Linux
- PowerShell (for Windows) or Bash (for macOS/Linux)
- Administrator/sudo privileges to install AWS CLI

## AWS CLI Installation

### Windows (PowerShell)

1. Open PowerShell as Administrator
2. Run the installation script:
   ```powershell
   .\install-aws-cli.ps1
   ```
3. If script execution is blocked, you may need to set execution policy:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```

### Manual Installation (All Platforms)

If the script doesn't work, you can manually install AWS CLI:

- **Windows**: Download and run [AWS CLI MSI Installer](https://awscli.amazonaws.com/AWSCLIV2.msi)
- **macOS**: Follow [AWS CLI macOS instructions](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-mac.html)
- **Linux**: Follow [AWS CLI Linux instructions](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-linux.html)

## GitHub Actions IAM Role Setup

After installing AWS CLI, you need to set up the IAM role for GitHub Actions.

### Windows (PowerShell)

```powershell
.\setup-github-actions-role.ps1 -Account YOUR_AWS_ACCOUNT_ID
```

### Linux/macOS (Bash)

```bash
./setup-github-actions-role.sh -a YOUR_AWS_ACCOUNT_ID
```

### Required Parameters

- `-Account/-a`: (Required) Your AWS account ID
- `-Profile/-p`: (Optional) AWS profile name (default: github-actions)
- `-Region/-r`: (Optional) AWS region (default: us-east-1)

### Customizing GitHub Repository Access

The script uses the policy defined in `github-actions-trust-policy.json`. By default, the policy allows access from repositories in the `acn-qiangchen/*` organization. You can modify this pattern in the JSON file to match your GitHub organization or repository pattern before running the setup scripts.

### What the Setup Script Does

1. Creates or updates an AWS profile with your credentials
2. Uses the template JSON file to create a customized policy
2. Sets up the OIDC provider for GitHub Actions in AWS IAM
3. Creates the `github-actions-role` with AdministratorAccess
4. Optionally creates ECR repositories for your services

## Using the GitHub Actions Workflow

After setup, you can manually trigger the GitHub Actions workflow:

1. Go to GitHub Actions tab in your repository
2. Select "Build and Push to ECR"
3. Click "Run workflow"
4. Enter:
   - Services to build (comma-separated, e.g., "hello-service,auth-service")
   - AWS Account Number
   - AWS Region (default: us-east-1)

## Troubleshooting

- **AWS CLI Not Found**: Restart your terminal/PowerShell after installation
- **Access Denied**: Verify your AWS credentials with `aws sts get-caller-identity`
- **OIDC Provider Error**: You may need to create it manually in AWS Console
- **Policy Document Error**: Ensure the `github-actions-trust-policy.json` file exists in the scripts directory 