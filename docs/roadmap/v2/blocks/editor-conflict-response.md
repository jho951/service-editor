# Editor Conflict Response v2 Roadmap

## 현재 기준

- v1 에디터 저장은 block 단위 optimistic lock으로 stale update를 막는다.
- stale version이면 `409 Conflict`와 공통 실패 응답(`GlobalResponse`)을 반환한다.
- 현재 응답의 `data`에는 충돌 block의 최신 `version`이나 `content`를 포함하지 않는다.
- 프론트는 conflict 후 필요한 최신 block 상태를 재조회하고, 현재 로컬 문서 상태 기준으로 pending을 다시 조립한다.

## 후속 Todo

- conflict 응답에 최신 block `version`과 `content`를 직접 포함할지 검토한다.
- 상세 payload를 넣는다면 단일 충돌 block만 담을지, batch 안의 모든 충돌 후보를 담을지 정한다.
- 공통 실패 응답 구조를 유지할지, editor save 전용 conflict 응답 타입을 둘지 정한다.
- stale update, optimistic lock 예외, bulk delete 경쟁 상태가 같은 응답 형식으로 표현 가능한지 확인한다.

## 검증 보강

- 같은 block에 대한 `BLOCK_REPLACE_CONTENT` 경쟁 테스트에서 최신 content 재조회 없이 복구 가능한지 검증한다.
- `BLOCK_MOVE`, `BLOCK_DELETE` 충돌도 상세 응답이 실제 UX 복구에 도움이 되는지 확인한다.
- 상세 응답을 추가할 경우 기존 `GlobalExceptionHandler`와 API 클라이언트의 하위 호환성을 검증한다.

## 정책 / 운영 고려사항

- conflict 응답에 본문 전체를 담으면 응답 크기와 개인정보 노출 범위가 커질 수 있다.
- 상세 payload는 프론트 복구 UX에 실제로 필요한 최소 필드로 제한한다.
- batch 전체 rollback 정책은 유지하고, 상세 응답은 복구 힌트 역할로만 사용한다.
