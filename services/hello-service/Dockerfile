# Build stage
FROM maven:3.8-amazoncorretto-17 AS builder
WORKDIR /app
# Copy pom.xml to download dependencies
COPY pom.xml .
# Download dependencies (separate layer for caching)
RUN mvn dependency:go-offline -B
# Copy source code
COPY src ./src
# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-al2023-jdk
WORKDIR /app
# Install shadow-utils (provides groupadd/useradd) and curl for healthcheck
RUN yum update -y && yum install -y shadow-utils curl && yum clean all
# Add a non-root user
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring
# Copy the JAR file from the build stage
COPY --from=builder /app/target/*.jar app.jar
# Expose application port
EXPOSE 8080
# Set healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application with proper JVM options
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"] 