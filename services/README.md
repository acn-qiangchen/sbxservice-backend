# SBXService Applications

This repository contains the application services for the SBXService platform. These services are designed to be deployed on AWS ECS Fargate with AWS App Mesh for service mesh capabilities.

## Services

- `hello-service`: A simple Spring Boot service that demonstrates basic functionality
- `user-service`: User management service
- `auth-service`: Authentication and authorization service

## Architecture

These services are part of a larger architecture that includes:
- AWS ECS Fargate for container orchestration
- AWS App Mesh for service mesh capabilities
- AWS Network Firewall for security
- AWS API Gateway for API access

For the complete infrastructure setup, see the [SBXService Infrastructure](https://github.com/your-org/sbxservice) repository.

## Getting Started

Each service has its own README.md with specific instructions for building and running locally.

### Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Docker
- AWS CLI configured with appropriate credentials

### Building Services

Each service can be built independently:

```bash
cd <service-name>
./mvnw clean package
```

### Running Locally

Each service can be run locally using Docker Compose:

```bash
cd <service-name>
docker-compose up
```

## Deployment

These services are designed to be deployed to AWS ECS Fargate. The deployment process is managed by the infrastructure repository.

## Development

1. Clone this repository
2. Make your changes
3. Build and test locally
4. Push changes to the repository
5. The CI/CD pipeline in the infrastructure repository will handle the deployment

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. 