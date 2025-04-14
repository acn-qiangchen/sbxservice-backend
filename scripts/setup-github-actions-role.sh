#!/bin/bash
set -e

# Help function
function show_help {
  echo "Usage: $0 [OPTIONS]"
  echo "Creates AWS profile and GitHub Actions role for ECR access"
  echo ""
  echo "Options:"
  echo "  -p, --profile NAME    AWS profile name (default: github-actions)"
  echo "  -r, --region REGION   AWS region (default: us-east-1)"
  echo "  -a, --account ID      AWS account ID (required)"
  echo "  -h, --help            Show this help message"
  exit 1
}

# Default values
PROFILE_NAME="github-actions"
REGION="us-east-1"
ACCOUNT_ID=""

# Parse arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    -p|--profile) PROFILE_NAME="$2"; shift ;;
    -r|--region) REGION="$2"; shift ;;
    -a|--account) ACCOUNT_ID="$2"; shift ;;
    -h|--help) show_help ;;
    *) echo "Unknown parameter: $1"; show_help ;;
  esac
  shift
done

# Validate required parameters
if [ -z "$ACCOUNT_ID" ]; then
  echo "Error: AWS account ID is required"
  show_help
fi

echo "=== Setting up GitHub Actions deployment for ECR ==="
echo "AWS Profile: $PROFILE_NAME"
echo "AWS Region: $REGION"
echo "AWS Account ID: $ACCOUNT_ID"
echo ""

# Prompt for AWS credentials
echo "Please enter AWS credentials for the profile:"
read -p "AWS Access Key ID: " AWS_ACCESS_KEY_ID
read -p "AWS Secret Access Key: " -s AWS_SECRET_ACCESS_KEY
echo ""

# Create or update AWS profile
echo "Creating/updating AWS profile..."
aws configure set aws_access_key_id "$AWS_ACCESS_KEY_ID" --profile "$PROFILE_NAME"
aws configure set aws_secret_access_key "$AWS_SECRET_ACCESS_KEY" --profile "$PROFILE_NAME"
aws configure set region "$REGION" --profile "$PROFILE_NAME"
aws configure set output "json" --profile "$PROFILE_NAME"

echo "AWS profile '$PROFILE_NAME' created/updated successfully."

# Create trust policy document with the correct account ID
echo "Creating trust policy for GitHub Actions..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TEMPLATE_PATH="$SCRIPT_DIR/github-actions-trust-policy.json"
POLICY_PATH="$SCRIPT_DIR/github-actions-trust-policy-temp.json"

# Read the template and replace the account ID
sed "s/ACCOUNT_ID/$ACCOUNT_ID/g" "$TEMPLATE_PATH" > "$POLICY_PATH"

# Check if the OIDC provider exists
echo "Checking if OIDC provider exists..."
if ! aws iam get-open-id-connect-provider --open-id-connect-provider-arn "arn:aws:iam::$ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" --profile "$PROFILE_NAME" 2>/dev/null; then
  echo "OIDC provider does not exist. Creating..."
  aws iam create-open-id-connect-provider \
    --url "https://token.actions.githubusercontent.com" \
    --client-id-list "sts.amazonaws.com" \
    --thumbprint-list "6938fd4d98bab03faadb97b34396831e3780aea1" \
    --profile "$PROFILE_NAME" || echo "Failed to create OIDC provider. You may need to create it manually in the AWS console."
fi

# Check if role exists
echo "Checking if role already exists..."
if aws iam get-role --role-name github-actions-role --profile "$PROFILE_NAME" 2>/dev/null; then
  echo "Role 'github-actions-role' already exists, updating trust policy..."
  aws iam update-assume-role-policy \
    --role-name github-actions-role \
    --policy-document file://"$POLICY_PATH" \
    --profile "$PROFILE_NAME"
else
  # Create role
  echo "Creating GitHub Actions role..."
  aws iam create-role \
    --role-name github-actions-role \
    --assume-role-policy-document file://"$POLICY_PATH" \
    --profile "$PROFILE_NAME"

  # Attach AdministratorAccess policy
  echo "Attaching AdministratorAccess policy..."
  aws iam attach-role-policy \
    --role-name github-actions-role \
    --policy-arn arn:aws:iam::aws:policy/AdministratorAccess \
    --profile "$PROFILE_NAME"
fi

# Create ECR repositories
echo "Do you want to create ECR repositories now? (y/n): "
read CREATE_REPOS

if [[ "$CREATE_REPOS" =~ ^[Yy]$ ]]; then
  read -p "Enter comma-separated service names (e.g., hello-service,auth-service): " SERVICES
  
  IFS=',' read -ra SERVICE_ARRAY <<< "$SERVICES"
  for SERVICE in "${SERVICE_ARRAY[@]}"; do
    SERVICE=$(echo "$SERVICE" | xargs)  # Trim whitespace
    REPO_NAME="sbxservice-$SERVICE"
    
    # Check if repository exists
    if ! aws ecr describe-repositories --repository-names "$REPO_NAME" --profile "$PROFILE_NAME" 2>/dev/null; then
      echo "Creating ECR repository: $REPO_NAME"
      aws ecr create-repository \
        --repository-name "$REPO_NAME" \
        --image-scanning-configuration scanOnPush=true \
        --profile "$PROFILE_NAME"
    else
      echo "ECR repository '$REPO_NAME' already exists, skipping creation."
    fi
  done
fi

# Clean up
rm -f "$POLICY_PATH"

echo ""
echo "=== Setup Complete ==="
echo "GitHub Actions role 'github-actions-role' has been created with AdminAccess"
echo "You can now use this role in your GitHub Actions workflows"
echo "AWS Account ID to use in workflow: $ACCOUNT_ID" 