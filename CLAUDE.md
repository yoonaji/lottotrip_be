# lottotrip (로또트립)

## 서비스 개요

유저가 조건(위치, 예산, 이동수단)을 입력하면 슬롯(룰렛)을 돌려 랜덤으로 **[여행지 1곳 + 미션 1개]** 를 뽑아주는 여행 추천 + 커뮤니티 서비스. 뽑은 결과를 저장해서 코스를 짜고, 현장 인증으로 미션을 완료하고, 촬영한 영상 클립을 모아 "산신령" 컨셉의 숏폼(릴스)을 자동 생성하며, 같은 여행지에 당첨된 사람들끼리 실시간 채팅방에서 만난다.

- 초기 타겟 지역: 강원도 (전국 확장을 고려해 `states` / `cities` 계층으로 설계됨)
- 여행지 데이터: 공공데이터 API(관광공사 등)에서 수급
- 미션 데이터: 직접 큐레이션 또는 AI 생성으로 오프라인 시딩 (런타임 기능 아님)

## 핵심 유저 플로우

1. **소셜 로그인**: 카카오 / 네이버 / 구글 → 자체 JWT(access/refresh) 발급
2. **슬롯 돌리기**: 유저 GPS + 예산 + 이동수단(`walk`/`car`) 기반 가중 랜덤 추첨
   - `walk`: 도보 + 대중교통 포함. 검색 반경 1km, `is_barrier_free=true` 장소만 후보
   - `car`: 자차. 검색 반경 20km
   - `saved_slots.budget` vs `places.estimated_cost`로 예산 필터링에 실제 사용
3. **슬롯 결과 저장/코스 구성**: 슬롯 결과를 `saved_slots`에 임시 저장 → 마음에 드는 것을 순서대로 골라 유저별 `travel_courses`에 추가. `course_items`는 장소/미션이 아니라 **그 슬롯을 돌린 컨텍스트(slot_id) 자체**를 참조함
4. **위치 인증 미션**: 도착 시 GPS 좌표가 적당한 반경 내에 들어오면 미션 완료(`user_missions`) 처리
5. **숏폼 생성**: 유저가 올린 S3 영상 클립을 지정 순서(`play_order`)대로 병합 + "산신령이 점지해 준..." TTS 대본을 입혀 비동기 렌더링. 자체 서버에서 처리하며, 필요 시 워커 프로세스로 분리 예정
6. **장소 기반 채팅방**: 당첨된 여행지(`place_id`) 기준으로 자동 합류되는 실시간 채팅방. **매일 새로 생성**되는 방식 — 같은 장소라도 날짜가 바뀌면 새 `chat_room` row가 생기고, 유저는 `place_id` + 오늘 날짜로 최신 방을 찾아 합류

## 기술 스택

- Spring Boot 4.1.0 / Java 17 (toolchain)
- Spring Security + OAuth2 Client (카카오/네이버/구글) + JWT (jjwt 0.13.0)
- Spring Data JPA + PostgreSQL 16 (로컬 단위테스트는 H2, 운영 클래스패스 제외)
- Spring Cloud AWS S3 (`spring-cloud-aws-starter-s3`)
- springdoc-openapi (Swagger UI: `/swagger-ui.html`, docs: `/v3/api-docs`)
- Testcontainers(Postgres) 기반 통합테스트
- Docker Compose로 로컬 DB(+앱) 구동

## 실행 방법

```bash
# 로컬 DB만 띄우고 앱은 IDE/gradle로 실행 (application-local.yml, ddl-auto: update)
docker compose up db

# 전체(앱+DB) 도커로 실행
docker compose up

# 테스트
./gradlew test
```

환경변수는 `.env.example` 참고 (`.env`로 복사해서 사용). 소셜 로그인 클라이언트 ID/Secret 없어도 dummy 값으로 기동은 됨 (해당 로그인 시도 시에만 실패).

## 프로젝트 현재 상태

스켈레톤 단계. `SecurityConfig`(actuator/health만 permitAll, 나머지 전부 인증 필요), OAuth2/JWT/S3 설정만 잡혀 있고 **도메인 엔티티·컨트롤러·서비스는 아직 없음**. ERD는 확정되어 있음 (아래).

## ERD

```sql
-- 0. ENUM 타입 정의
CREATE TYPE oauth_provider AS ENUM ('KAKAO', 'NAVER', 'GOOGLE');
CREATE TYPE travel_category AS ENUM ('FOOD', 'ACTIVITY', 'NATURE', 'CULTURE', 'SHOPPING'); -- 참고용, places.category는 의도적으로 VARCHAR로 느슨하게 둠
CREATE TYPE transport_type AS ENUM ('walk', 'car');
CREATE TYPE media_type AS ENUM ('IMAGE', 'VIDEO');
CREATE TYPE job_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE mission_status AS ENUM ('PENDING', 'SUCCESS', 'FAIL');

-- 1. 유저 관리
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE social_auth (
    auth_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    provider oauth_provider NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. 행정 구역 코드 (전국 확장 대비)
CREATE TABLE states (
    state_id INT PRIMARY KEY,
    state_name VARCHAR(20) NOT NULL
);

CREATE TABLE cities (
    city_id INT PRIMARY KEY,
    state_id INT REFERENCES states(state_id) ON DELETE CASCADE,
    city_name VARCHAR(30) NOT NULL
);

-- 3. 여행지 정보
CREATE TABLE places (
    place_id BIGSERIAL PRIMARY KEY,
    city_id INT REFERENCES cities(city_id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    coordinate POINT NOT NULL,       -- GiST 인덱스용 (경도, 위도)
    estimated_cost INT DEFAULT 0,
    is_barrier_free BOOLEAN DEFAULT FALSE, -- 뚜벅이용 무장애 플래그
    thumbnail_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_places_coordinate ON places USING gist (coordinate);

-- 4. 위치 기반 미션 마스터
CREATE TABLE missions (
    mission_id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES places(place_id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    guide_description TEXT,
    guide_image_url TEXT,
    reward_point INT DEFAULT 0
);

-- 5. 슬롯(룰렛) 결과 저장
CREATE TABLE saved_slots (
    slot_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    latitude DECIMAL(10, 7) NOT NULL,  -- 슬롯 돌릴 당시 유저 GPS
    longitude DECIMAL(10, 7) NOT NULL,
    budget INT NOT NULL,
    transport transport_type NOT NULL,
    place_id BIGINT REFERENCES places(place_id) ON DELETE CASCADE,
    mission_id BIGINT REFERENCES missions(mission_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. 여행 코스 및 상세 항목
CREATE TABLE travel_courses (
    course_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(100) DEFAULT '내 여행 코스',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE course_items (
    item_id BIGSERIAL PRIMARY KEY,
    course_id BIGINT REFERENCES travel_courses(course_id) ON DELETE CASCADE,
    slot_id BIGINT REFERENCES saved_slots(slot_id) ON DELETE CASCADE,
    sequence INT NOT NULL DEFAULT 1,
    added_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. 유저 미션 수행 이력
CREATE TABLE user_missions (
    user_mission_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    mission_id BIGINT REFERENCES missions(mission_id) ON DELETE CASCADE,
    certified_coord POINT,               -- 미션 완료 시 유저 GPS 좌표
    certified_media_url TEXT,
    status mission_status DEFAULT 'SUCCESS',
    certified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. 숏폼 비디오/렌더링 작업 관리
CREATE TABLE shortforms (
    job_id VARCHAR(100) PRIMARY KEY,      -- 비동기 작업 ID (ex: render_a1b2c3)
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    video_url TEXT,                       -- COMPLETED 시점에 최종 S3 URL
    status job_status DEFAULT 'PENDING',
    progress INT DEFAULT 0,               -- 렌더링 진행률 (0~100)
    tts_script TEXT NOT NULL,             -- 대사 텍스트
    narration_type VARCHAR(50) DEFAULT 'sanshilling',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shortform_clips (
    clip_id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) REFERENCES shortforms(job_id) ON DELETE CASCADE,
    clip_url TEXT NOT NULL,
    play_order INT NOT NULL               -- 클립 재생 순서
);

-- 9. 장소 기반 운명 공동체 채팅방
CREATE TABLE chat_rooms (
    room_id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES places(place_id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_users (
    room_id BIGINT REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE chat_messages (
    message_id BIGSERIAL PRIMARY KEY,
    room_id BIGINT REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_chat_messages_room_time ON chat_messages (room_id, created_at DESC);
```

## 팀 분담

**개발자 A — 핵심 여행 흐름 (인증·시스템·슬롯·코스·미션)**
로그인/토큰 갱신/로그아웃, 헬스 체크, 슬롯 돌리기/조회(가중 랜덤 + TourAPI 배치 파이프라인), 코스 추가/조회/삭제, 미션 완료 처리. DB 스키마·비즈니스 로직 중심으로 사용자 여정의 뼈대를 처음부터 끝까지 담당.

**개발자 B — 미디어·실시간 인프라 (영상·채팅)** ← 이 세션에서 작업하는 파트
업로드 URL 발급 / 릴스 생성 요청 / 릴스 상태 조회 (S3 Presigned, Lambda+FFmpeg, Polly TTS, 비동기 job), 채팅방 목록 조회 / 채팅 이력 조회 + WebSocket(STOMP) 실시간 통신.
