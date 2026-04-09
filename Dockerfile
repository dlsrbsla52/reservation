# === Builder Stage ===
FROM ubuntu:24.04 AS builder

# 필수 패키지 설치
RUN apt-get update && apt-get install -y wget unzip findutils

# JDK 25 설치 레이어 고정
# 상단에 배치하여 하위 소스코드가 변경되더라도 JDK 다운로드 레이어는 재사용되도록 구성합니다.
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

# 의존성 정의 파일 우선 복사
# 소스코드 복사 이전에 환경 설정 파일만 복사하여 의존성 다운로드 레이어를 별도로 캐싱합니다.
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew

# 하위 모듈 소스 전체 복사
COPY modules modules

ARG MODULE_NAME

# 최신 트렌드: BuildKit Cache Mount 및 Gradle 병렬 처리 적용
# 1. --mount=type=cache,target=/root/.gradle: 컨테이너 간 Gradle 의존성 저장소를 공유하여 중복 다운로드를 방지합니다.
# 2. 기존 코드의 clean 태스크를 제거하고 --build-cache 옵션을 활용하여 재컴파일 시간을 단축합니다.
# 3. --parallel 옵션으로 가용한 시스템 리소스를 활용해 태스크를 병렬로 실행합니다.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :modules:${MODULE_NAME}:bootJar --parallel --build-cache --no-daemon

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

# 빌더 스테이지에서 생성된 결과물 복사 [cite: 8]
COPY --from=builder /workspace/modules/${MODULE_NAME}/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]