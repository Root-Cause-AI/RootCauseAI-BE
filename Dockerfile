# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application, skipping tests to speed up the build in Render
RUN mvn clean package -DskipTests

# Stage 2: Create the production image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the compiled jar from the builder stage
# Spring Boot Maven plugin generates a jar in target directory
COPY --from=builder /app/target/*.jar app.jar

# Expose the dynamic port (used by Render)
EXPOSE ${PORT}

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
