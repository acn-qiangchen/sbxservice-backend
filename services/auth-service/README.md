# Authentication Service

## Overview

The Authentication Service provides authentication, authorization, and token management for the SBXService microservice architecture. It integrates with AWS Cognito for secure identity management and supports role-based access control.

## Key Features

- User authentication (login/logout)
- JWT token generation and validation
- OAuth2/OIDC integration
- Role-based access control
- Session management
- Password reset and account recovery

## API Endpoints

The service exposes the following API endpoints:

- `POST /auth/login` - Authenticate a user and issue tokens
- `POST /auth/logout` - Invalidate user tokens
- `POST /auth/refresh` - Refresh an access token using a refresh token
- `GET /auth/validate` - Validate a token
- `POST /auth/password-reset/request` - Request a password reset
- `POST /auth/password-reset/confirm` - Confirm a password reset
- `GET /auth/roles` - Get roles for a user
- `POST /auth/register` - Register a new user (if not using User Service)

## Technology Stack

- **Language**: [TBD]
- **Framework**: [TBD]
- **Identity Provider**: AWS Cognito
- **Token Storage**: DynamoDB (for token blacklisting/revocation)
- **API Documentation**: OpenAPI/Swagger
- **Containerization**: Docker
- **Deployment**: AWS ECS/EKS

## Development Setup

[TBD based on chosen technology stack]

## Environment Variables

- `COGNITO_USER_POOL_ID` - AWS Cognito User Pool ID
- `COGNITO_CLIENT_ID` - AWS Cognito App Client ID
- `COGNITO_REGION` - AWS Region for Cognito
- `JWT_SECRET` - Secret for signing JWTs (if not using Cognito for tokens)
- `TOKEN_EXPIRY` - Access token expiry time in seconds
- `REFRESH_TOKEN_EXPIRY` - Refresh token expiry time in seconds
- `DYNAMODB_TABLE` - DynamoDB table for token management
- `LOG_LEVEL` - Logging level (default: info)
- `PORT` - Port to run the service on (default: 8081)

## Building and Running

[TBD based on chosen technology stack]

## Testing

[TBD based on chosen technology stack]

## Deployment

The service is deployed as a container in the AWS ECS/EKS cluster. Deployment is managed through the CI/CD pipeline.

## Security Considerations

- All endpoints are HTTPS-only
- Tokens are short-lived
- Refresh tokens are rotated on use
- IP-based rate limiting for login attempts
- Brute force protection
- Secure password requirements

## Integration with Other Services

All other microservices in the SBXService architecture integrate with the Authentication Service for:
- Validating user tokens
- Checking user permissions
- Obtaining user context

## Monitoring and Logging

- AWS CloudWatch for metrics and logs
- Login attempt monitoring
- Failed authentication alerts
- Token usage metrics

## Future Enhancements

- Multi-factor authentication
- Biometric authentication support
- Enterprise SSO integration
- Advanced threat detection 