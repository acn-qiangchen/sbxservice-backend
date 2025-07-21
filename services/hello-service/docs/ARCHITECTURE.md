# Hello Service Architecture

This document provides an overview of the Hello Service architecture, explaining key components and their interactions.

## Architectural Overview

The Hello Service follows a layered architecture approach, common in Spring Boot applications:

```
┌─────────────────┐
│    Controller   │ ← REST API endpoints
├─────────────────┤
│    Service      │ ← Business logic
├─────────────────┤
│    Model        │ ← Data representation
└─────────────────┘
```

## Component Details

### Application Layer

**HelloServiceApplication**: The main entry point for the Spring Boot application. It initializes the application context and enables auto-configuration.

### Controller Layer

**HelloController**: Handles incoming HTTP requests and delegates to the service layer.
- Exposed endpoints: `GET /api/hello`
- Accepts optional "name" parameter
- Returns a JSON response with a detailed greeting message
- Includes request context information (headers, user agent)
- Includes server information (hostname, system properties)
- Includes OpenAPI documentation annotations

### Service Layer

**HelloService**: Contains the business logic for generating greeting messages.
- Takes a name input and generates a personalized greeting
- Returns a default greeting if no name is provided
- Collects detailed system information including hostname, OS, and memory metrics
- Extracts request header information
- Configuration-driven default message

### Model Layer

**GreetingResponse**: Represents the data structure returned by the API.
- Contains the greeting message
- Includes request timestamp
- Captures client information (user agent)
- Contains a map of all request headers
- Includes nested ServerInfo class with system details:
  - Hostname
  - Java version
  - OS name and version
  - Memory usage statistics (free/total)
- Serialized to JSON in responses

### Configuration

**OpenApiConfig**: Configures Swagger/OpenAPI documentation.
- Sets up API information
- Configures available server endpoints

**WebConfig**: Configures web-related settings.
- Enables CORS (Cross-Origin Resource Sharing) for all endpoints
- Allows requests from any origin
- Permits common HTTP methods (GET, POST, PUT, DELETE, etc.)
- Sets appropriate caching headers

## Cross-Cutting Concerns

### Configuration Management

The application uses Spring's property management with profile-based configuration:
- `application.yml`: Default configuration
- `application-{profile}.yml`: Environment-specific settings

#### JSON Configuration

The service uses Jackson for JSON serialization with the following configurations:

**Pretty Printing**: 
- JSON responses are pretty-printed (indented) globally across all environments for better readability and debugging
- This applies to all JSON responses from the service, making complex nested objects like `GreetingResponse` easier to read

Configuration is managed through Spring Boot's Jackson properties:
```yaml
spring:
  jackson:
    serialization:
      indent-output: true  # Enable pretty printing globally
```

**Response Format**:
- All JSON responses are formatted with multi-line, indented output for improved readability

### Monitoring and Observability

Spring Boot Actuator provides built-in endpoints:
- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

The enhanced response model also provides additional observability into:
- System metrics (memory usage)
- Client request patterns (headers, user agents)
- Runtime environment details

### API Documentation

OpenAPI (Swagger) provides automatic API documentation:
- UI: `/swagger-ui.html`
- JSON docs: `/api-docs`

## Technology Stack

- **Framework**: Spring Boot 3.2.x
- **Language**: Java 17
- **Build Tool**: Maven
- **API Documentation**: SpringDoc OpenAPI
- **Testing**: JUnit 5, MockMvc, Mockito

## Runtime Flow

1. Client sends a request to `GET /api/hello?name=John`
2. Spring Web routes the request to `HelloController`
3. Controller extracts the query parameter and HttpServletRequest
4. Controller calls `HelloService.generateDetailedGreeting("John", request)`
5. Service generates greeting message and collects:
   - Current timestamp
   - Client's User-Agent header
   - All request headers
   - Server information (hostname, OS, Java version, memory metrics)
6. Service returns a comprehensive `GreetingResponse` object
7. Spring converts the response to JSON and returns it to the client

## Example Response

```json
{
  "message": "Hello, John!",
  "timestamp": "2025-04-28T10:19:43.960502+09:00",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
  "requestHeaders": {
    "host": "localhost:8080",
    "user-agent": "Mozilla/5.0 ...",
    "accept": "application/json",
    "accept-language": "en-US,en;q=0.9"
  },
  "serverInfo": {
    "hostname": "app-server-1",
    "javaVersion": "17.0.8",
    "osName": "Linux",
    "osVersion": "5.15.0-1031-aws",
    "freeMemory": 256,
    "totalMemory": 512
  }
}
```

## Containerization

The application is containerized using Docker:
- Multi-stage build for optimization
- Base image: Eclipse Temurin JRE 17 Alpine
- Health checks configured
- JVM optimized for containerized environments

## Testing Strategy

The application includes several types of tests:
- **Unit Tests**: Test individual components in isolation
- **Web Layer Tests**: Test controllers with mocked services
- **Integration Tests**: Test the full application context
- **Response Validation Tests**: Verify that all expected fields are present

## Security Considerations

While this is a simple service with no explicit security mechanisms, in a production environment consider:
- Adding authentication using Spring Security
- Implementing rate limiting
- Adding HTTPS with proper certificate management
- Evaluating information disclosure risks from server details
- Restricting CORS to specific trusted domains instead of allowing all origins

## Scalability

The service is designed to be stateless, allowing for horizontal scaling:
- No session state is maintained
- No local caching that would cause consistency issues
- Health checks for load balancer integration

## Future Enhancements

Potential improvements for this service:
- Add authentication and authorization
- Add metrics for business KPIs
- Implement caching for high-volume scenarios
- Add database integration for storing greetings
- Implement circuit breakers for fault tolerance
- Add client geolocation information
- Track request processing time 