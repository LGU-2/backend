# fm-backend

신선식품 자사몰 백엔드. Java 21, Spring Boot 4.0.5, MySQL 8.4, Gradle.

```bash
git clone https://github.com/fresh-market/fm-backend.git backend
cd backend
./gradlew bootRun      # compose.yaml 의 MySQL 을 자동으로 띄운다
```

**처음 왔다면 [docs/project-guideline.md](./docs/project-guideline.md) 부터 본다.**
작업 흐름, 병합을 막는 조건, 코드를 쓸 때 볼 문서가 거기에 있다.

| | |
|---|---|
| [project-guideline.md](./docs/project-guideline.md) | 시작점. 여기부터 |
| [git-convention.md](./docs/git-convention.md) | 브랜치와 커밋 규칙 |
| [code-architecture/domain-map.md](./docs/code-architecture/domain-map.md) | 13개 도메인과 층 |
| [verification/verification-guide.md](./docs/verification/verification-guide.md) | 검증 도구 사용법 |
| [위키](https://github.com/fresh-market/fm-backend/wiki) | 회의 결과, 문제 해결 공유 |

작업은 [프로젝트 보드](https://github.com/orgs/fresh-market/projects/6) 의 이슈에서 시작한다.
