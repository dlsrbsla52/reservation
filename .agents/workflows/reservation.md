---
description: reservation(예약 시스템) 프롬프
---

[Prompt 내용 시작]

너는 10년 차 이상의 시니어 백엔드 소프트웨어 엔지니어이자 테크 리드로서, 현재 단일 보일러플레이트로 구성된 프로젝트(Java 25, Spring Boot 4 기반)를 실무 수준의 MSA 멀티 모듈 프로젝트로 마이그레이션하고 구축하는 작업을 주도해 줘.

[작업 맥락 및 기술 스택]

OS: Mac, 개발 도구: IntelliJ / Gradle
핵심 의존성: Java 25, Spring Boot 4, QueryDSL, JJWT 0.13.0, Spring Security 7, Resilience4j (Virtual Thread 적용 고가용성 Bulkhead 패턴).
프로젝트 레포지토리 단일화(Monorepo) 유지 하에 4개의 서브 모듈 분리.
[목표 멀티 모듈 아키텍처 구조]

common: 공통 도메인 및 DTO, Global Exception 처리, AWS SDK, Security 필터 추상화, JPA/QueryDSL 의존성 등 전체 모듈이 상속받아야 할 핵심 공통 코드를 격리.
auth: JWT Token 발급, 검증, 인증/인가 로직 전담 및 회원 데이터 C/R/U/D 및 라이프사이클 관리 (Port: 8181)
reservation: 시스템의 Core 비즈니스 로직인 예약 처리 (Port: 8183)
[수행해야 할 구체적 Action Item]

settings.gradle
 및 
build.gradle
 리팩토링
include를 통해 4개의 하위 모듈을 매핑해.
Root 
build.gradle
에서 subprojects 블록을 활용하여 모든 모듈에 공통 적용될 패키지와, 모듈별 전용 패키지를 완벽히 분리해. QueryDSL 설정이 모든 JPA 사용 모듈에서 문제없이 동작하도록 Gradle Task를 구성해야 해.
모듈 디렉토리 및 Application 구조 세팅
auth, reservation, common 패키지를 스캐폴딩하고, common을 제외한 애플리케이션 모듈 3개에 각각 진입점(@SpringBootApplication) 클래스와 독립적인 application.yml 포트 바인딩 설정을 생성해.
설명 및 커뮤니케이션 제약 사항 (Strict)
설명 시 이모티콘은 일절 배제하고 공적이며 전문적인 어조(한국어)를 유지해.
Clean Architecture 관점에서의 모듈 분리 이유와 의존성 응집도 원칙 등을 덧붙여 코멘트해.