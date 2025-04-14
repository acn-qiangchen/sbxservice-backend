# User Management Service

## Overview

The User Management Service handles user profiles, accounts, and preferences in the SBXService microservice architecture. It provides a RESTful API for creating, retrieving, updating, and deleting user data.

## Key Features

- User registration and profile management
- User preferences storage and retrieval
- Account status management
- Integration with Authentication Service
- Role-based access management

## API Endpoints

The service exposes the following API endpoints:

- `POST /users` - Create a new user
- `GET /users/{userId}` - Retrieve a user by ID
- `PUT /users/{userId}` - Update a user
- `DELETE /users/{userId}` - Delete a user
- `GET /users` - List users (with pagination and filtering)
- `GET /users/{userId}/preferences` - Get user preferences
- `PUT /users/{userId}/preferences` - Update user preferences

## Technology Stack

- **Language**: [TBD]
- **Framework**: [TBD]
- **Database**: Amazon RDS (PostgreSQL)
- **API Documentation**: OpenAPI/Swagger
- **Containerization**: Docker
- **Deployment**: AWS ECS/EKS

## Development Setup

[TBD based on chosen technology stack]

## Environment Variables

- `DB_HOST` - Database host
- `DB_PORT` - Database port (default: 5432)
- `DB_NAME` - Database name
- `DB_USER` - Database username
- `DB_PASSWORD` - Database password
- `AUTH_SERVICE_URL` - URL of the Authentication Service
- `LOG_LEVEL` - Logging level (default: info)
- `PORT` - Port to run the service on (default: 8080)

## Building and Running

[TBD based on chosen technology stack]

## Testing

[TBD based on chosen technology stack]

## Deployment

The service is deployed as a container in the AWS ECS/EKS cluster. Deployment is managed through the CI/CD pipeline.

## Database Schema

```
users
- id (UUID, primary key)
- email (string, unique)
- first_name (string)
- last_name (string)
- status (enum: active, inactive, suspended)
- created_at (timestamp)
- updated_at (timestamp)

user_preferences
- user_id (UUID, foreign key to users.id)
- preference_key (string)
- preference_value (jsonb)
- updated_at (timestamp)
```

## Dependencies

- Authentication Service - For user authentication and authorization
- Data Service - For user-related data processing

## Monitoring and Logging

- AWS CloudWatch for metrics and logs
- Structured JSON logs
- Request tracing with correlation IDs

## Future Enhancements

- Multi-factor authentication support
- Enhanced privacy controls
- User activity tracking
- User analytics 