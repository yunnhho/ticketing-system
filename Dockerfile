# 1. Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
# Gradle 실행 권한 부여 및 빌드 (테스트 제외하여 속도 향상)
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar

# 2. Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# 빌드된 jar 파일만 복사
COPY --from=build /app/build/libs/*.jar app.jar
# 실행 시 프로파일을 prod로 설정 (컨테이너 간 통신용) Heap 메모리 75%할당
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "-Dspring.profiles.active=prod", "app.jar"]