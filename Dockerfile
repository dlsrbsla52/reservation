# JDK 버전·체크섬 고정. `latest` URL은 Docker 레이어 캐시 때문에 패치 후에도 예전 JDK가 남을 수 있어 정확한 빌드를 지정한다.
# 배포판은 Amazon Corretto(AWS 무상 LTS 지원). 25.0.5.1.2: 2026-11 보안 패치(CVE-2026-114938 등) 반영 빌드.
# 올릴 때는 버전과 체크섬을 함께 바꾼다. 체크섬은 https://github.com/corretto/corretto-25/releases 의 linux-aarch64 tar.gz SHA256.
# 대상 아키텍처는 ARM64(aarch64) 고정이다. compose는 `platform: linux/arm64`로 빌드·실행하고, 다른 아키텍처로 빌드하면 아래 검사에서 실패한다.
ARG JDK_VERSION=25.0.5.1.2
ARG JDK_SHA256=9370d3e5bcf0d165e6be4a78c3f6b57160af16d3033319af583800251eb91df2

# 레이어 구성 요약 (아래로 갈수록 자주 바뀐다)
#   jdk     : OS + Corretto. 빌더와 런타임이 같은 레이어를 공유해 JDK를 한 번만 받는다.
#   deps    : Gradle 배포판 + 외부 의존성 JAR. 빌드 스크립트(*.gradle.kts)가 바뀔 때만 다시 만든다.
#   builder : 소스 컴파일과 bootJar. 코드가 바뀌면 여기부터 다시 실행된다.
#   runtime : Spring Boot 레이어(dependencies → loader → snapshot → application) 순서로 복사해,
#             코드만 바뀌면 application 레이어(약 1MB)만 새로 만들어진다.

# === JDK Stage (빌더·런타임 공통) ===
FROM amazonlinux:2023 AS jdk
ARG JDK_VERSION
ARG JDK_SHA256

# less: 컨테이너 안 로그 확인용으로 남긴다. cat·tail은 기본 이미지(coreutils-single)에 이미 있다.
# tar·gzip은 압축 해제에만 필요해 같은 레이어에서 설치하고 지운다(런타임 이미지 공격 표면·크기 축소).
RUN dnf install -y less tar gzip && \
    arch=$(uname -m) && \
    if [ "$arch" != "aarch64" ]; then \
    echo "ARM64(aarch64) 전용 이미지다. 현재 아키텍처: $arch (--platform linux/arm64 로 빌드할 것)" && exit 1; \
    fi && \
    curl -fsSL "https://corretto.aws/downloads/resources/${JDK_VERSION}/amazon-corretto-${JDK_VERSION}-linux-aarch64.tar.gz" -o jdk.tar.gz \
    && echo "${JDK_SHA256}  jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/jdk-25 \
    && tar -xzf jdk.tar.gz -C /opt/jdk-25 --strip-components=1 \
    && rm jdk.tar.gz \
    && dnf remove -y tar gzip \
    && dnf clean all

ENV JAVA_HOME=/opt/jdk-25
ENV PATH=$JAVA_HOME/bin:$PATH

# 기본 로케일(POSIX)에서는 JVM의 stdout/stderr 인코딩이 ASCII가 되어 로그의 한글이 '?'로 찍힌다.
# AL2023 glibc에 내장된 C.UTF-8로 맞추면 JVM 출력과 셸(less 등)이 모두 UTF-8이 된다.
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# === Builder Base ===
FROM jdk AS builder-base
# findutils: gradlew가 쓰는 xargs와 아래 빌드 스크립트 추출의 find. unzip: Gradle 배포판 해제 보조.
RUN dnf install -y findutils unzip && dnf clean all
WORKDIR /workspace

# === Gradle Scripts Stage ===
# 모듈 디렉터리에서 build.gradle.kts만 남긴다. 모듈을 추가해도 Dockerfile을 고칠 필요가 없고,
# 소스만 바뀐 경우 결과물이 같아 다음 단계의 COPY --from이 캐시를 그대로 쓴다.
FROM builder-base AS gradle-scripts
COPY modules modules
RUN find modules -type f ! -name 'build.gradle.kts' -delete && find modules -type d -empty -delete

# === Deps Stage ===
# 외부 의존성을 이미지 레이어(/opt/gradle-seed)에 받아 둔다. 캐시 마운트가 아니라 레이어에 두는 이유는
# `--cache-to`(레지스트리 캐시)로 내보낼 수 있게 하기 위해서다. 캐시 마운트 내용은 내보내지지 않는다.
FROM builder-base AS deps
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY --from=gradle-scripts /workspace/modules modules

# Docker 빌드 전용 init script. 소스 없이 빌드 스크립트만으로 외부 의존성(플러그인·라이브러리·Kotlin 컴파일러) JAR를 받는다.
# Docker에서만 쓰므로 저장소 파일로 두지 않고 여기서 만든다(IDE는 init script에 Gradle API를 연결하지 않아 오류로 표시한다).
# - 프로젝트 간 의존성은 빌드해야 생기는 산출물이라 제외하고 외부 모듈만 받는다.
# - 해석할 수 없는 configuration이 있어도 lenient로 넘긴다. 빠진 것은 builder 단계에서 받으므로 빌드 결과에는 영향이 없다.
COPY <<'EOF' /opt/resolve-dependencies.init.gradle.kts
allprojects {
    afterEvaluate {
        tasks.register("resolveDependencies") {
            val artifacts = configurations
                .filter { it.isCanBeResolved }
                .map { configuration ->
                    configuration.incoming.artifactView {
                        lenient(true)
                        componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                    }.files
                }
            doLast { artifacts.forEach { it.files } }
        }
    }
}
EOF

# 읽기 전용 의존성 캐시(GRADLE_RO_DEP_CACHE)로 쓰려면 Gradle 문서대로 잠금 파일과 gc.properties를 지워야 한다.
RUN chmod +x ./gradlew \
    && GRADLE_USER_HOME=/opt/gradle-seed ./gradlew resolveDependencies \
       --init-script /opt/resolve-dependencies.init.gradle.kts --no-daemon -q \
    && find /opt/gradle-seed -name '*.lock' -delete \
    && find /opt/gradle-seed -name 'gc.properties' -delete

# === Builder Stage ===
FROM deps AS builder
# 실행 모듈은 modules/app 하나다(모듈러 모놀리스). 도메인 모듈은 app.jar 안에 라이브러리로 들어간다.
COPY modules modules

# - GRADLE_RO_DEP_CACHE: deps 단계에서 받은 의존성을 읽기 전용으로 재사용한다(빌드 스크립트가 안 바뀌면 다운로드 0).
# - /root/.gradle 캐시 마운트: 같은 빌드 머신에서 Gradle 빌드 캐시(--build-cache)를 이어 써서 바뀐 모듈만 다시 컴파일한다.
#   마운트가 비어 있으면(새 머신·CI) 레이어의 Gradle 배포판을 복사해 재다운로드를 피한다.
# - --parallel: 모듈을 병렬로 빌드한다.
ENV GRADLE_RO_DEP_CACHE=/opt/gradle-seed/caches
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    if [ ! -d /root/.gradle/wrapper/dists ]; then mkdir -p /root/.gradle && cp -r /opt/gradle-seed/wrapper /root/.gradle/; fi \
    && ./gradlew :modules:app:bootJar --parallel --build-cache --no-daemon \
    && java -Djarmode=tools -jar modules/app/build/libs/app.jar extract --layers --destination /workspace/extracted

# === Runtime Stage ===
FROM jdk
WORKDIR /app

# Spring Boot 레이어를 변경 빈도가 낮은 순서로 복사한다. 실행은 기존과 같이 `java -jar app.jar`이며,
# 추출된 app.jar는 매니페스트 Class-Path로 lib/의 의존성을 읽는다(이미지 밖으로 꺼내 단독 실행하는 용도는 아니다).
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

ENTRYPOINT ["java", "-jar", "app.jar"]
