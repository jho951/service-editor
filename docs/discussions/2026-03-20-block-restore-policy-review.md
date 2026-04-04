# 2026-03-20 블록 복구 API 및 브라우저 세션 undo 정책 검토 메모

## 문서 목적

- 블록 삭제 복구를 server API로 둘지, 브라우저 세션 undo/redo로 한정할지 검토한다.
- 문서 복구 API는 유지하고 블록 복구 API는 v1에서 제외할지 결정 근거를 남긴다.
- 이 문서는 회의록 성격의 검토 메모이며, 채택안은 ADR로 승격한다.

## 배경

- 현재 v1 문서 삭제는 soft delete이며, 문서 복구 시 하위 문서와 소속 블록을 함께 복구한다.
- 블록 삭제는 하위 블록 포함 soft delete를 기본 정책으로 두고 있다.
- 팀은 undo/redo를 브라우저 세션 범위의 로컬 캐시 기반 기능으로 운영하고, 탭 또는 브라우저 종료 후 복구는 지원하지 않는 방향을 검토 중이다.
- 이 전제에서는 "방금 삭제한 블록 복구"와 "서버에 반영된 삭제 상태 복구"를 분리해 볼 필요가 있다.

## 검토 범위

- v1 블록 삭제 복구 책임을 클라이언트와 서버 중 어디에 둘지
- 외부 유사 플랫폼의 삭제, 복구, 버전 이력 정책 비교
- v1 요구사항과 API 범위 정리

이번 문서에서 다루지 않는 항목:

- v2 이후 협업 편집용 OT/CRDT 도입 여부
- 브라우저 종료 후 복구를 위한 IndexedDB 영속 초안
- 감사 로그 저장소 상세 설계

## 핵심 질문

1. 블록 단위 restore API가 실제 v1 사용자 시나리오에 필요한가
2. 브라우저 세션 한정 undo/redo만으로 v1 편집 복구 요구를 충분히 커버하는가
3. 문서 복구 API와 블록 복구 API를 모두 둘 때 생기는 정책 복잡도를 감수할 가치가 있는가

## 고려한 자료와 사례

- 내부 요구사항: [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- 내부 작업 로그: [prompts/worklog/2026-03/2026-03-20-document-restore-api.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-document-restore-api.md)
- Notion Help, Duplicate, delete, and restore content: <https://www.notion.com/help/duplicate-delete-and-restore-content>
- Notion Help, Reset Notion: <https://www.notion.com/help/reset-notion>
- Google Docs Editors Help, Find out what's changed in a file: <https://support.google.com/docs/answer/190843>
- Google Workspace Docs product page: <https://workspace.google.com/products/docs/>
- Atlassian Support, Delete, restore, or purge a content item: <https://support.atlassian.com/confluence-cloud/docs/delete-restore-or-purge-a-page/>
- Atlassian Support, Create, update, and manage written content: <https://support.atlassian.com/confluence-cloud/docs/create-edit-and-publish-a-page/>
- Atlassian Support, How to use the new toolbar and insert elements: <https://support.atlassian.com/confluence-cloud/docs/how-to-use-the-new-toolbar-and-insert-elements/>

외부 사례 관찰 요약:

- Notion은 블록 삭제 자체는 가능하지만, 공식 복구 수단은 페이지 Trash와 페이지 version history 중심이다.
- Google Docs는 문서 단위 version history와 restore를 강하게 제공한다. 복구 단위는 기본적으로 document version이다.
- Confluence는 편집 중 undo/redo를 제공하면서도, 영속 복구는 page trash와 page version history 중심으로 다룬다.

위 요약 중 "블록 단위 server restore를 별도 제공하지 않는다"는 부분은 각 공식 문서에서 명시적 금지로 적혀 있다기보다, 공식 복구 수단이 문서/페이지/버전 중심으로 노출된다는 점에서 도출한 해석이다.

## 선택지

### 선택지 1. 블록 restore API를 v1에 포함

#### 개요

- `POST /v1/blocks/{blockId}/restore`를 두고, 삭제된 블록 서브트리를 서버에서 복구한다.
- 문서 복구 API와 별도로 활성 문서 안의 부분 복구를 지원한다.

#### 시나리오

1. 사용자가 활성 문서에서 섹션 블록 하나를 삭제하고 저장까지 반영한다.
2. 이후 같은 세션이 아니거나, 문서 전체 복구 없이 특정 블록만 되살리고 싶어 한다.
3. 서버는 대상 블록 존재 여부, 부모 블록 상태, 원래 위치 복원 가능 여부를 검증한 뒤 복구를 수행한다.
4. 부모가 이미 삭제되었거나 sibling 배치 규칙이 달라졌다면 실패 정책이나 대체 위치 정책을 추가로 정의해야 한다.

#### 장점

- 부분 복구를 서버 책임으로 제공할 수 있다.
- 브라우저 세션이 끊긴 뒤에도 블록 단위 복구 시나리오를 열어둘 수 있다.
- 향후 휴지통이나 감사 로그 기반 수동 복구로 이어가기 쉽다.

#### 단점

- 부모 블록 상태, 원래 위치, 이동 후 재복구 규칙 등 추가 정책이 많아진다.
- 문서 복구와 블록 복구의 경계가 겹쳐 API 의미가 흐려진다.
- v1 편집 복구 문제를 서버가 대신 떠안아 구현 복잡도와 테스트 범위가 커진다.

#### 트레이드오프

- 세션 종료 후 부분 복구 가능성을 얻는 대신, v1 범위를 넘어서는 트리 복구 정책 비용을 지불한다.

#### 적합한 상황

- 블록 휴지통, 장기 보관 복구, 운영자 수동 복원, 협업 삭제 복구가 v1 핵심 요구일 때

### 선택지 2. 브라우저 세션 undo/redo + 문서 restore만 유지

#### 개요

- 브라우저 세션 안의 직전 편집 복구는 클라이언트 undo/redo가 담당한다.
- 영속 복구는 문서 soft delete 복구만 서버가 담당한다.
- 블록 restore API는 v1 범위에서 제외한다.

#### 시나리오

1. 사용자가 문서 편집 중 블록 내용을 수정하거나 블록을 삭제한다.
2. `setTimeout` 저장 전이거나 저장 직후라도 같은 브라우저 세션 안에서는 undo/redo 스택으로 직전 변경을 되돌린다.
3. 사용자가 문서 자체를 삭제한 경우에는 서버의 문서 복구 API로 문서와 소속 블록을 함께 복구한다.
4. 탭이나 브라우저를 닫으면 undo/redo 스택은 폐기되고, 이후에는 문서 복구처럼 서버가 보장하는 복구만 사용한다.

#### 장점

- v1 범위를 명확하게 줄일 수 있다.
- 사용자가 기대하는 "방금 한 실수 되돌리기"는 더 빠른 클라이언트 동작으로 해결된다.
- 서버는 문서 단위 영속 복구와 낙관적 락 유지에 집중할 수 있다.

#### 단점

- 브라우저 세션이 끝나면 블록 단위 복구는 불가능하다.
- 다른 기기나 다른 사용자가 블록 삭제를 복구하는 시나리오는 지원하지 않는다.
- 추후 블록 휴지통을 도입하려면 정책을 다시 열어야 한다.

#### 트레이드오프

- 세션 범위 복구에 집중해 구현 복잡도를 줄이는 대신, 장기적 부분 복구는 v2 이후 과제로 남긴다.

#### 적합한 상황

- v1 목표가 빠른 편집 경험과 명확한 서버 범위 정의에 있고, 장기 보관형 부분 복구가 핵심이 아닐 때

## 비교 요약

- 선택지 1은 복구 범위는 넓지만, 트리 복구 규칙과 API 의미가 급격히 복잡해진다.
- 선택지 2는 편집 중 실수 복구와 영속 삭제 복구를 분리해 책임 경계가 선명하다.
- 외부 사례도 대체로 "편집 중 되돌리기"와 "문서/페이지 단위 영속 복구"를 분리해 제공한다.

## 추천 시나리오

- 사용자가 활성 문서에서 블록을 하나 지웠다가 3초 뒤 바로 되돌리고 싶을 때는 브라우저 undo가 가장 자연스럽다.
- 사용자가 문서 자체를 휴지통에서 되살리고 싶을 때는 문서 restore API가 더 자연스럽다.
- 이 둘을 분리하면 사용자는 "방금 한 행동 취소"와 "삭제된 문서 복구"를 서로 다른 계층의 기능으로 이해할 수 있다.

## 현재 추천 방향

- v1에서는 브라우저 세션 범위 undo/redo를 채택한다.
- v1에서는 `block restore` server API를 요구사항과 API 목록에서 제외한다.
- 문서 soft delete 복구 API는 유지한다.
- 블록 삭제 후 복구 요구는 같은 세션 내에서는 클라이언트 undo/redo로 해결한다.
- 세션 종료 후 부분 복구가 필요한 운영 시나리오는 v2 이후 별도 정책으로 재검토한다.

추천 이유:

- 현재 제품에서 즉시 필요한 복구는 "방금 한 편집 취소" 성격이 강하다.
- 문서 삭제와 블록 삭제는 사용자 기대 복구 단위가 다르다.
- 문서 복구는 영속 상태 전이지만 블록 되돌리기는 편집 UX에 가깝다.
- 외부 유사 서비스도 대체로 문서/페이지 단위 영속 복구를 중심으로 노출한다.
- v1에서 가장 비싼 복잡도는 블록 트리 부분 복구 규칙 정의다.

## 미해결 쟁점

1. 세션 안에서 undo/redo stack 최대 깊이를 얼마로 둘지
2. autosave debounce 시점과 undo checkpoint 기준을 어떻게 맞출지
3. v2에서 블록 휴지통이나 감사 로그 기반 부분 복구가 필요한지

## 다음 액션

1. `docs/REQUIREMENTS.md`에서 `block restore` API와 관련 정책을 제거한다.
2. 채택 결정은 ADR로 승격한다.
3. v2 후속 검토 문서에 "세션 종료 후 부분 복구" 재검토 조건을 남긴다.

## 관련 문서

- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [013-adopt-session-scoped-browser-undo-and-drop-block-restore-api.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/013-adopt-session-scoped-browser-undo-and-drop-block-restore-api.md)
- [block-restore.md](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/v2/blocks/block-restore.md)
- [2026-03-20-block-restore-policy.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-block-restore-policy.md)
