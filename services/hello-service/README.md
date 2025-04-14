# Hello Service

## Overview

Hello Service is a simple Spring Boot microservice that provides a "Hello World" API. It serves as the first microservice implementation in the sbxservice project.

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker

### Run Locally

```bash
# Using Maven
mvn spring-boot:run

# Using Docker Compose
docker-compose up
```

Access the service at:
- API: http://localhost:8080/api/hello
- Swagger UI: http://localhost:8080/swagger-ui.html

### Using VS Code Dev Containers (Simplest Setup)

For the simplest development experience, use VS Code with Dev Containers:

1. Install [VS Code](https://code.visualstudio.com/) and the [Remote - Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension

2. Create `.devcontainer/devcontainer.json` in the project root:
```json
{
  "name": "Hello Service Development",
  "dockerComposeFile": "../docker-compose.yml",
  "service": "hello-service",
  "workspaceFolder": "/app",
  "extensions": [
    "vscjava.vscode-java-pack",
    "vmware.vscode-spring-boot",
    "redhat.vscode-yaml"
  ],
  "forwardPorts": [8080],
  "remoteUser": "spring"
}
```

3. Create `.devcontainer/Dockerfile` (optimized for development):
```dockerfile
FROM maven:3.8-openjdk-17

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring
RUN mkdir -p /home/spring && chown -R spring:spring /home/spring

# Set up development environment
RUN apt-get update && apt-get install -y git curl

# Set up working directory
WORKDIR /app
RUN chown -R spring:spring /app

# Set the user
USER spring

# Keep container running
CMD ["sleep", "infinity"]
```

4. Open VS Code, click on the Remote Containers icon in the bottom-left corner, and select "Reopen in Container"

All prerequisites will be installed in the container, and you can develop without installing Java, Maven, or other tools locally!

## Development Tasks

### Build and Test

```bash
# Build the application
mvn clean package

# Run tests
mvn test
```

### Docker Operations

```bash
# Build Docker image
docker build -t sbxservice/hello-service .

# Build for AWS (if using Apple Silicon/M1/M2)
docker buildx build --platform linux/amd64 -t sbxservice/hello-service .

# Run locally
docker run -p 8080:8080 sbxservice/hello-service
```

### Deploy to AWS ECR

```bash
# 1. Export the ECR repository URL as an environment variable
# (Replace with your actual ECR URL from Terraform output)
export AWS_PROFILE=sbxservice-poc && export ECR_REPO_URL=$(aws ecr describe-repositories --query "repositories[?repositoryName=='sbxservice-dev-repo'].repositoryUri" --output text) && echo "Using ECR Repository: ${ECR_REPO_URL}" && aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ECR_REPO_URL}

# 2. Authenticate with ECR
# aws ecr get-login-password --region us-east-1 --profile your-aws-profile | \
#   docker login --username AWS --password-stdin $ECR_REPO_URL

# # 3. Tag the image
# docker tag sbxservice/hello-service:latest ${ECR_REPO_URL}:latest

# # 4. Push to ECR
# docker push ${ECR_REPO_URL}:latest
# ```

docker tag sbxservice/hello-service:latest ${ECR_REPO_URL}:latest && date && docker push ${ECR_REPO_URL}:latest && date && echo "Image push complete"
```

For convenience, you can add this to your `.bashrc` or `.zshrc`:

```bash
# Add this to your shell configuration file
export ECR_REPO_URL=123456789012.dkr.ecr.us-east-1.amazonaws.com/sbxservice-hello
```

## Configuration

Run with specific profile:
```bash
mvn spring-boot:run -Dspring.profiles.active=local
```

Change port:
```bash
mvn spring-boot:run -Dserver.port=8081
```

## Troubleshooting

### Port already in use
```bash
mvn spring-boot:run -Dserver.port=8081
```

### Docker permission issues
```bash
sudo chmod 666 /var/run/docker.sock
```

## API Endpoints

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/api/hello` | Returns a greeting message | `name` (optional): Name to include in greeting |
| GET | `/actuator/health` | Health check endpoint | None |
| GET | `/swagger-ui.html` | API documentation UI | None |

## Technology Stack

- Spring Boot 3.2.x
- Java 17
- Maven
- Docker
- JUnit 5 & Mockito (testing)
- SpringDoc OpenAPI (API documentation)

## Architecture

This service follows a standard layered architecture with controller, service, and model components. For details, see `docs/ARCHITECTURE.md`.

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Docker Documentation](https://docs.docker.com/) 



----
export AWS_PROFILE=sbxservice-poc && export ECR_REPO_URL=$(aws ecr describe-repositories --query "repositories[?repositoryName=='sbxservice-dev-repo'].repositoryUri" --output text) && echo "Using ECR Repository: ${ECR_REPO_URL}" && aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ECR_REPO_URL}


docker tag sbxservice/hello-service:latest ${ECR_REPO_URL}:latest && date && docker push ${ECR_REPO_URL}:latest && date && echo "Image push complete"

aws ecs update-service --cluster sbxservice-dev-cluster --service sbxservice-dev-service --force-new-deployment --output text