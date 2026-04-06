# Discussions

이 디렉터리는 아직 채택되지 않은 설계 검토, 전략 비교, 회의 메모를 보관한다.

운영 원칙은 다음과 같다.

- 공통 문서 흐름, 중복 제거, 외부 자료 표기 원칙은 먼저 [docs/README.md](https://github.com/jho951/Block-server/blob/dev/docs/README.md)를 따른다.
- 요구사항 해석, 전략 비교, 장단점 검토, 회의 기록은 이 디렉터리에 남긴다.
- 되돌리기 어렵거나 팀 합의가 필요한 선택이 실제로 채택되면 [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md)에 ADR로 승격한다.
- discussion에서 실제 채택으로 이어졌다면, discussion의 `관련 문서`에는 대응 ADR 링크를 함께 남긴다.
- 구현 작업 로그와 AI 작업 메모는 [prompts/](https://github.com/jho951/Block-server/blob/dev/prompts/README.md)에 남긴다.
- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)에는 현재 유효한 요구사항과 채택된 정책만 반영한다.
- 새 문서를 만들 때는 [docs/discussions/000-strategy-review-template.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/000-strategy-review-template.md)를 기준으로 구조를 맞춘다.
- `review`, `strategy`, `meeting`처럼 문서 이름이 달라도 구조는 같은 템플릿 계열로 맞춘다.
- 전략 설명, 비교, 추천에는 시나리오를 반드시 함께 적는다.
- discussion은 "이 선택지가 무엇인가"를 설명한 뒤, 같은 흐름 안에서 "왜 지금은 이걸 택하거나 보류하는가"까지 이어서 끝내야 한다.
- 참고한 자료와 사례는 가능하면 `배경` 말미의 하위 구간으로 흡수해 문맥이 끊기지 않게 한다. 자료 목록 자체가 문서의 핵심 비교 대상일 때만 독립 섹션으로 분리한다.
- 외부 공식 문서나 외부 사례를 참고했다면, 본문에서도 `공식 문서 기준`, `공개 자료 기준`, `외부 사례를 현재 구조에 대입하면` 같은 방식으로 출처 성격과 해석 여부가 드러나게 적는다.

권장 문서 형식은 다음과 같다.

1. 작업 목적
2. 배경
3. 검토한 선택지
4. 비교 요약
5. 현재 추천 방향
6. 미해결 쟁점
7. 다음 액션
8. 관련 문서

관련 문서 예시:

- 블록 저장 전략 검토
- 저장 API와 일반 수정 API의 역할 분리 검토
- 템플릿: [docs/discussions/000-strategy-review-template.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/000-strategy-review-template.md)
