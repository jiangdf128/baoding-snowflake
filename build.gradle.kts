plugins {
    id("java-library")
    id("org.springframework.boot") version "4.0.4" apply false
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.4")
    }
}

dependencies {
    // Spring Boot Starter（仅用于自动配置）
    compileOnly("org.springframework.boot:spring-boot-starter")

    // Redis（要求 8.0+）
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Spring Boot 配置处理器
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

    // 测试
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}