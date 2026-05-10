plugins {
    id("java-library")
    id("org.springframework.boot") version "4.0.4" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("signing")
}

group = "io.github.jiangdf128"
version = "1.0.0"

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
    compileOnly("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("io.lettuce:lettuce-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "baoding-snowflake"
            version = "1.0.0"

            pom {
                name.set("BaoDing Snowflake")
                description.set("云原生环境分布式ID生成器 / Distributed ID generator for cloud-native")
                url.set("https://github.com/jiangdf128/baoding-snowflake")

                licenses {
                    license {
                        name.set("The Unlicense")
                        url.set("https://unlicense.org/")
                    }
                }

                developers {
                    developer {
                        id.set("jiangdf128")
                        name.set("蒋德福")
                        email.set("jiangcan_110@126.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/jiangdf128/baoding-snowflake.git")
                    developerConnection.set("scm:git:https://github.com/jiangdf128/baoding-snowflake.git")
                    url.set("https://github.com/jiangdf128/baoding-snowflake")
                }
            }
        }
    }

    repositories {
        maven {
            name = "Sonatype"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = "vLiXvv"
                password = "rFn7lLefUlv1lpWdkx05VtXZbRqC3qxwI"
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}