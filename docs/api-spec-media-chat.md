# API 명세 — 영상/채팅 (개발자 B)

Notion 원본 명세를 기반으로 구현 전 확정한 버전. Notion에도 이 내용으로 반영 필요.

> **정정 (2026-08-22)**: 이 문서는 원래 공통 응답 형식을 `{status, data, error}`(`status`가 HTTP status code)로 가정하고 작성됐다. 이후 A의 공통 모듈(`common.response.ApiResponse`)로 통일하면서 실제 응답은 `{success, data, error}`(`success`는 boolean)로 나간다. 아래 예시의 `"status": 200`은 전부 `"success": true`로 읽어야 한다 — **프론트는 `success` 필드를 봐야 하고, `status` 필드는 존재하지 않는다.** `data` 안의 `status`(예: `"PENDING"`, `"COMPLETED"`)는 잡 상태값이라 별개이며 그대로 유효하다.

---

## 1. 업로드 URL 발급

`POST /api/v1/video/upload-urls`

인증 필요.

### Request

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fileCount | integer | O | 업로드할 클립 개수 (1~10) |
| contentType | string | X | 파일 형식, 기본 `video/mp4`. 한 요청의 모든 파일에 동일하게 적용 (이미지/영상 섞어 올리려면 요청을 나눠서 호출) |

```json
{ "fileCount": 2, "contentType": "video/mp4" }
```

### Response (200)

> **변경**: 각 업로드 항목에 `clipUrl`(서명 없는 순수 S3 URL)과 `order`, `expiresIn`을 추가했다. `uploadUrl`은 PUT 전용 presigned URL(서명 포함)이라 업로드 후 재사용 불가 — 릴스 생성 요청 시에는 반드시 `clipUrl`을 사용해야 한다.

```json
{
  "success": true,
  "data": {
    "uploads": [
      { "order": 1, "uploadUrl": "https://s3.../clip1.mp4?X-Amz-Signature=...", "clipUrl": "https://s3.../clip1.mp4", "expiresIn": 600 },
      { "order": 2, "uploadUrl": "https://s3.../clip2.mp4?X-Amz-Signature=...", "clipUrl": "https://s3.../clip2.mp4", "expiresIn": 600 }
    ]
  },
  "error": null
}
```

클라이언트는 `order` 순서대로 업로드하고, 이후 `/video/render` 호출 시 각 클립의 `clipUrl`을 그대로 `clips[].clipUrl`에 담아 보낸다.

---

## 2. 릴스 생성 요청

`POST /api/v1/video/render`

인증 필요. HTTP 상태코드는 200으로 응답 (job은 비동기로 PENDING 시작).

### Request

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| clips | array | O | 클립 정보 배열 |
| clips[].clipUrl | string | O | 업로드 URL 발급 응답의 `clipUrl` |
| clips[].order | integer | O | 재생 순서 |
| clips[].caption | string | X | 이 클립이 재생되는 동안 화면에 번인되는 짧은 자막. 없으면 자막 없이 재생 |
| ttsScript | string | O | 더빙 대사 텍스트 (음성 내레이션용, 화면 자막과는 별개) |
| narrationType | string | X | 더빙 컨셉 (`sanshilling` 등), 기본 `sanshilling` |

> **추가**: 클립별 화면 자막(`caption`) 필드 추가. `ttsScript`는 Polly로 음성 변환되는 내레이션 오디오고, `caption`은 ffmpeg `drawtext`로 영상에 직접 번인되는 텍스트라 서로 완전히 다른 트랙이다 — 둘 다 쓸 수도, 자막만 쓰고 내레이션 없이(`ttsScript`는 필수라 빈 문자열은 안 되고 최소 텍스트는 있어야 함) 쓸 수도 있음.

```json
{
  "clips": [
    { "clipUrl": "https://s3.../clip1.mp4", "order": 1, "caption": "산신령이 점지해 준" },
    { "clipUrl": "https://s3.../clip2.mp4", "order": 2, "caption": "동쪽 바다로 가거라" }
  ],
  "ttsScript": "산신령이 점지해 준 동쪽 바다로 가거라",
  "narrationType": "sanshilling"
}
```

### Response (200)

> **변경**: 원본 예시는 HTTP 202 / body `status` 200으로 서로 달랐던 걸 200으로 통일했다. 다른 엔드포인트들도 전부 200 기준이라 통일성을 맞춤 (작업이 아직 시작 안 됐다는 건 body의 `status: "PENDING"`으로 충분히 표현됨).

```json
{
  "success": true,
  "data": { "jobId": "render_a1b2c3", "status": "PENDING" },
  "error": null
}
```

---

## 3. 릴스 상태 조회

`GET /api/v1/video/render/{jobId}`

인증 필요.

| 파라미터 | 위치 | 타입 | 설명 |
|---|---|---|---|
| jobId | path | string | 렌더링 작업 ID |

### Response (200) — 처리 중

```json
{
  "success": true,
  "data": { "jobId": "render_a1b2c3", "status": "PROCESSING", "progress": 45, "videoUrl": null },
  "error": null
}
```

### Response (200) — 완료

```json
{
  "success": true,
  "data": { "jobId": "render_a1b2c3", "status": "COMPLETED", "progress": 100, "videoUrl": "https://s3.../final/render_a1b2c3.mp4" },
  "error": null
}
```

### Response (200) — 실패

> **추가**: 원본 명세에 없던 FAILED 케이스. HTTP 요청 자체는 성공(200)이고 job의 결과가 실패이므로 실패 사유는 top-level `error`가 아니라 `data.failReason`에 담는다.

```json
{
  "success": true,
  "data": { "jobId": "render_a1b2c3", "status": "FAILED", "progress": 60, "videoUrl": null, "failReason": "FFMPEG_MERGE_FAILED" },
  "error": null
}
```

`progress` 단계 기준: 클립 다운로드 25 → TTS 합성 50 → 영상 병합 75 → S3 업로드 100.

---

## 4. 채팅방 목록 조회

`GET /api/v1/chat/rooms`

인증 필요. Body 없음. 내가 참여 중인 모든 운명 공동체 채팅방(과거 포함) 목록.

### Response (200)

```json
{
  "success": true,
  "data": {
    "rooms": [
      { "roomId": 7001, "placeName": "사천진해변", "memberCount": 4 }
    ]
  },
  "error": null
}
```

---

## 5. 채팅 이력 조회

`GET /api/v1/chat/rooms/{roomId}/messages`

인증 필요.

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| roomId | path | integer | O | 채팅방 ID |
| cursor | query | string | X | 페이징 커서 (이전 응답의 `nextCursor`) |
| size | query | integer | X | 한 번에 가져올 메시지 수 (기본 20) |

### Response (200)

```json
{
  "success": true,
  "data": {
    "messages": [
      { "messageId": 88012, "senderId": 1, "nickname": "감자러버", "messageText": "안녕하세요!", "createdAt": "2026-08-03T10:15:00+09:00" }
    ],
    "nextCursor": "eyJpZCI6ODgwMTJ9"
  },
  "error": null
}
```

> **해결 (2026-08-22)**: 옵션 ② — 한 모놀리식 안이라 `ChatService`가 A 도메인의 `UserRepository`를 직접 주입받아 `senderId`로 닉네임을 조회한다 (별도 내부 API 없이 바로 DB 조회, `findAllById`로 이력 조회 시 N+1 방지). 탈퇴 회원의 과거 발언도 닉네임 그대로 보여준다 — 이미 인증에서 탈퇴 여부는 걸러진 뒤라 이력 표시 목적으로는 지울 이유가 없음. 커서(`nextCursor`)는 마지막 `messageId`를 base64 JSON(`{"id":...}`)으로 인코딩한 값으로 구현함.

---

## 6. 채팅방 자동 생성/합류 (REST API 아님, 내부 로직)

> **결정**: 클라이언트가 명시적으로 "방 참가"를 호출하는 API는 없음 (명세에도 없었음). 슬롯 당첨 시점에 서버가 자동으로 처리한다.

B 쪽 구현은 끝났음 — **A가 해야 할 일은 슬롯 저장 성공 후 이벤트 한 줄 발행하는 것뿐**:

```java
applicationEventPublisher.publishEvent(new SlotDrawnEvent(userId, placeId, place.getName()));
```

- `SlotDrawnEvent(Long userId, Long placeId, String placeName)` — `com.lottotrip.common.event.SlotDrawnEvent` (구현 완료). `placeName`을 이벤트에 실어 보내는 이유: B가 아직 Place 엔티티에 접근할 수단이 없어서, A가 이미 들고 있는 장소명을 그대로 넘겨받기 위함.
- B의 `com.lottotrip.chat.event.ChatRoomAutoJoinListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 수신해서 처리 (구현 완료):
  1. `chat_rooms`에서 `place_id = placeId AND created_at`이 오늘(KST) 범위인 방이 있는지 조회
  2. 없으면 새로 생성 (`title`에는 장소명을 그대로 저장, 예: "사천진해변" — 채팅방 목록 조회의 `placeName` 응답 필드가 이 값을 그대로 사용하기 때문)
  3. `chat_users`에 `(room_id, user_id)`가 없으면 insert
- 이 방식으로 A/B가 서로의 서비스 클래스를 직접 참조하지 않고 이벤트로만 연결됨 (한 Spring Boot 모놀리식 안이라 실제 메시지 큐는 불필요).
- **삭제 로직은 별도로 두지 않음** — 방은 날짜별로 계속 쌓이고, 과거 방은 "채팅방 목록/이력 조회"에서 읽기 전용 히스토리로 남는다. 필요해지면 추후 보관 주기 정책(예: N일 지난 방 아카이빙)을 별도로 논의.

---

## 7. 실시간 통신 (WebSocket / STOMP)

- 연결: `ws://api.lottotrip.com/ws`
- 구독(수신): `/sub/chat/rooms/{roomId}`
- 발행(송신): `/pub/chat/rooms/{roomId}`

### 인증

CONNECT 프레임의 `Authorization` STOMP 헤더로 JWT access token 전달 (`Authorization: Bearer <token>`). URL 쿼리 파라미터로 토큰을 넣지 않는다 — 프록시/로그에 노출될 수 있기 때문. 서버는 `ChannelInterceptor`에서 `StompCommand.CONNECT`일 때만 토큰을 검증하고, 인증된 유저를 세션의 Principal로 바인딩해 이후 SEND/SUBSCRIBE에서 재사용한다.

> **구현 상태 (2026-08-22 갱신)**: `com.lottotrip.chat.ws.StompAuthInterceptor`가 이제 실제 JWT를 검증한다 (`JwtProvider.getUserIdFromAccessToken`, HTTP 쪽 `JwtAuthenticationFilter`와 동일한 방식) — 토큰이 없거나 위조·만료됐거나 탈퇴한 회원이면 CONNECT 자체를 거부한다. 헤더 이름/형식은 원래 설계 그대로라 클라이언트 쪽 변경 없음.

### 발행 (클라이언트 → 서버)

`/pub/chat/rooms/{roomId}`

```json
{ "messageText": "안녕하세요!" }
```

`senderId`는 클라이언트가 보내지 않는다 — 서버가 STOMP 세션의 Principal에서 추출한다 (클라이언트 위조 방지). 서버는 발신자가 해당 `roomId`의 `chat_users` 멤버인지 확인 후(아니면 `CHAT_001 NOT_ROOM_MEMBER`), `chat_messages`에 저장하고 브로드캐스트한다.

### 구독 (서버 → 클라이언트)

`/sub/chat/rooms/{roomId}` — REST 이력 조회의 메시지 객체와 동일한 형태로 통일.

```json
{
  "messageId": 88013,
  "roomId": 7001,
  "senderId": 1,
  "nickname": "감자러버",
  "messageText": "안녕하세요!",
  "createdAt": "2026-08-03T10:16:00+09:00"
}
```

### 에러 처리

SEND 처리 중 실패(멤버 아님, 방 없음 등)는 브로드캐스트하지 않고 발신자 개인 큐로만 전달: `/user/queue/errors`, 공통 에러 포맷(`{code, message}`) 그대로 사용.

---

## 8. 에러 코드 추가분

기존 `ErrorCode`에 다음 추가 필요:

| code | HTTP | 설명 |
|---|---|---|
| VIDEO_002 | 400 | `fileCount`가 허용 범위(1~10)를 벗어남 |
| VIDEO_003 | (job 내부, HTTP 아님) | 렌더링 실패 — `failReason`에 저장되는 실제 코드는 `RenderWorker`가 단계별로 부여: `CLIP_DOWNLOAD_FAILED`(S3에서 클립 다운로드 실패), `TTS_SYNTHESIS_FAILED`(Polly 합성 실패), `FFMPEG_MERGE_FAILED`(클립별 자막 번인 포함 병합 실패), `S3_UPLOAD_FAILED`(최종본 업로드 실패), `UNKNOWN_ERROR`(그 외) |

> **미검증 리스크**: `caption` 번인은 ffmpeg `drawtext` 필터(폰트 `Noto Sans CJK KR`, Dockerfile에 `fonts-noto-cjk` 설치)로 구현했는데, 실제 ffmpeg 환경에서 한글 자막이 정상 렌더링되는지 한 번도 확인 못 했다. 실사용 전에 실제 클립으로 한글 자막 번인이 깨지지 않는지 반드시 확인 필요 (`FFMPEG_MERGE_FAILED`가 자꾸 나면 이 부분부터 의심).

`CHAT_001`(NOT_ROOM_MEMBER), `CHAT_002`(ROOM_NOT_FOUND)는 기존 정의 그대로 재사용.

---

## 9. A가 해야 할 일 정리

1. ~~JWT 인증 필터~~ — 완료. `DevHeaderAuthFilter` 삭제됨, `JwtAuthenticationFilter`가 principal name에 userId(문자열)를 넣어서 `CurrentUserIdArgumentResolver`가 그대로 동작함.
2. ~~STOMP CONNECT 인증~~ — 완료 (2026-08-22). `StompAuthInterceptor`가 실제 JWT 검증으로 교체됨.
3. **SlotDrawnEvent 발행 — 아직 안 됨.** 프로젝트 전체에 `ApplicationEventPublisher` 사용처가 0건. 슬롯 저장 성공 코드 마지막에 아래 한 줄만 추가하면 된다 (6장 참고). **이거 없으면 채팅방 자동 합류 기능이 전혀 동작하지 않음 — `GET /api/v1/chat/rooms`가 항상 빈 배열.**
   ```java
   applicationEventPublisher.publishEvent(new SlotDrawnEvent(userId, placeId, place.getName()));
   ```
4. ~~채팅 nickname~~ — 완료 (2026-08-22). B가 `UserRepository`를 직접 조회하는 방식으로 해결.
