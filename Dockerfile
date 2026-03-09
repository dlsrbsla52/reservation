# === Builder Stage ===
FROM ubuntu:24.04 AS builder

# 필요한 패키지 설치 및 Java 25 설치 준비
RUN apt-get update && apt-get install -y wget unzip findutils

# Java 25 JDK 다운로드 및 설치
RUN arch=$(uname -m) && \
    if [ "$arch" = "x86_64" ]; then \
    url="https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz"; \
    elif [ "$arch" = "aarch64" ]; then \
    url="https://download.oracle.com/java/25/latest/jdk-25_linux-aarch64_bin.tar.gz"; \
    else \
    echo "Unsupported architecture: $arch" && exit 1; \
    fi && \
    wget $url -O jdk.tar.gz \
    && mkdir -p /opt/jdk-25 \
    && tar -xzf jdk.tar.gz -C /opt/jdk-25 --strip-components=1 \
    && rm jdk.tar.gz

ENV JAVA_HOME=/opt/jdk-25
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /workspace

# Gradle 캐시 활용을 위해 설정 파일 먼저 복사
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# 하위 모듈 설정 복사
COPY modules modules

# 빌드 권한 부여 및 특정 모듈 빌드
# 캐시 이슈 방지를 위해 clean 태스크를 추가하였습니다.
RUN chmod +x ./gradlew
ARG MODULE_NAME
RUN ./gradlew clean :modules:${MODULE_NAME}:bootJar -x test

# === Runtime Stage ===
FROM ubuntu:24.04

# 런타임용 Java 25 설치
RUN apt-get update && apt-get install -y wget && \
    arch=$(uname -m) && \
    if [ "$arch" = "x86_64" ]; then \
    url="https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz"; \
    elif [ "$arch" = "aarch64" ]; then \
    url="https://download.oracle.com/java/25/latest/jdk-25_linux-aarch64_bin.tar.gz"; \
    fi && \
    wget $url -O jdk.tar.gz \
    && mkdir -p /opt/jdk-25 \
    && tar -xzf jdk.tar.gz -C /opt/jdk-25 --strip-components=1 \
    && rm jdk.tar.gz \
    && apt-get clean

ENV JAVA_HOME=/opt/jdk-25
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /app
ARG MODULE_NAME

# 빌더 스테이지에서 생성된 실행 가능한 jar 파일만 복사
# 와일드카드 사용 시 구 버전 파일이 섞이지 않도록 명확하게 지정하는 것이 좋으나,
# builder 단계에서 clean을 수행하므로 현재 구조를 유지하며 안전하게 복사합니다.
COPY --from=builder /workspace/modules/${MODULE_NAME}/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]