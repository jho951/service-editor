# 개인 학습 문서 운영 메모

이 폴더는 작업 산출물용 공식 문서가 아니라, 프로젝트를 진행하면서 생긴 학습 질문을 다시 볼 수 있게 정리하는 개인 학습 노트 영역이다.

`.gitignore` 대상이므로, 여기서는 PR 친화성보다 아래 두 가지를 우선한다.

- 다음 질문이 들어왔을 때 기존 설명을 이어서 확장할 수 있는 구조
- 개념 설명과 현재 저장소 맥락이 바로 연결되는 설명 방식

이 경로의 문서는 팀의 공식 검토 기록, 채택 근거, 운영 정책 문서로 취급하지 않는다.

- Git 저장소에 반영되거나 PR로 공유되는 공식 문서라고 가정하지 않는다.
- 공유용 참조나 PR 본문 링크 대상으로 사용하지 않는다.
- 다른 사람이 저장소 기준으로 공식 산출물을 찾는 경로처럼 다루지 않는다.
- 문제 해결 과정을 기록하는 문서는 [docs/learn/troubleshooting/](https://github.com/jho951/Block-server/blob/dev/docs/learn/troubleshooting/README.md)에 두고, 해당 경로를 다룰 때는 [docs/learn/troubleshooting/README.md](https://github.com/jho951/Block-server/blob/dev/docs/learn/troubleshooting/README.md)를 먼저 읽는다.
- 공통 문서 흐름, 중복 제거, 외부 자료 표기 원칙은 먼저 [docs/README.md](https://github.com/jho951/Block-server/blob/dev/docs/README.md)를 따른다.

## 기본 디렉토리 규칙

- 주제는 도메인 디렉토리로 나눈다.
- 예:
  - `infra/`
  - `concurrency/`
  - `spring/`
  - `testing/`
  - `database/`

## 파일명 규칙

- 날짜보다 주제 중심의 고정 파일명을 쓴다.
- 좋은 예:
  - `infra/infra-ci-cd-and-github-actions.md`
  - `concurrency/serializing-answer-and-countdownlatch.md`
- 피할 예:
  - `2026-03-31-ci-cd-study.md`
  - `today-question.md`

같은 주제의 후속 질문이면 새 파일을 만들지 말고 기존 문서를 확장한다.

## 문서 종류

### 1. 인덱스형 문서

여러 문서를 이어서 공부해야 하는 주제에 사용한다.

- 파일명 예: `infra-learning-map.md`
- 역할:
  - 읽는 순서 제시
  - 각 문서의 범위 설명
  - 다음 학습 지점 연결

### 2. 주제 설명형 문서

하나의 질문이나 개념을 깊게 설명할 때 사용한다.

- 파일명 예: `serializing-answer-and-countdownlatch.md`

## 권장 문서 흐름

새 학습 문서를 만들거나 기존 문서를 크게 보강할 때는 가능하면 아래 순서를 따른다.

1. `왜 지금 이걸 보는가`
2. `먼저 잡을 핵심 개념`
3. `이 프로젝트에서 보면 어디에 적용되는가`
4. `요청/실행 흐름 또는 시나리오`
5. `자주 헷갈리는 포인트`
6. `다음에 이어서 볼 질문`

모든 문서가 이 순서를 완벽히 따를 필요는 없지만, 적어도 아래 두 요소는 꼭 들어가는 편이 좋다.

- 저장소 안의 실제 코드 경로 또는 실행 경로
- 추상 개념이 아니라 실제 요청/충돌/배포 같은 시나리오

## 설명 톤 기준

- 교과서 전체 요약보다 현재 repo 기준 이해를 우선한다.
- 정의만 나열하지 말고 "그래서 여기서는 무엇을 보면 되는가"까지 연결한다.
- 비교가 필요하면 좋은 경우와 나쁜 경우를 같이 적는다.
- follow-up 질문이 예상되면 문서 마지막에 다음 질문 후보를 남긴다.
- 개인 학습 문서는 교과서 요약보다 현재 저장소 맥락 연결을 더 강하게 우선한다.

## 새 요청이 왔을 때 작성 플로우

1. 같은 주제 문서가 이미 있는지 먼저 찾는다.
2. 있으면 그 문서를 확장한다.
3. 없으면 도메인 디렉토리를 정하고 새 주제 문서를 만든다.
4. 시리즈가 될 가능성이 높으면 `...-learning-map.md`도 함께 만든다.
5. 답변 본문에서는 핵심만 요약하고, 자세한 설명은 이 폴더 문서로 축적한다.

## 현재 예시

- 인프라 시리즈:
  - `infra/infra-learning-map.md`
  - `infra/infra-runtime-basics.md`
  - `infra/infra-docker-and-compose.md`
  - `infra/infra-env-and-secrets.md`
  - `infra/infra-ci-cd-and-github-actions.md`
  - `infra/infra-msa-deployment.md`
  - `infra/infra-terraform-and-iac.md`
- 동시성 설명:
  - `concurrency/serializing-answer-and-countdownlatch.md`
