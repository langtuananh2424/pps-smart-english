# PPS Smart English

| Thư mục | Công nghệ |
|---|---|
| `Web/` | React 19 + Vite + TypeScript |
| `Backend/` | Spring Boot 3.5, Java 21, Maven, Flyway |
| `Mobile/` | Flutter |
| DB | PostgreSQL 17 |

## Chạy bằng Docker Compose

```bash
cp .env.example .env     # đổi POSTGRES_PASSWORD
docker compose up --build
```

- Web: http://localhost:3000 (nginx proxy `/api` sang backend)
- Backend: http://localhost:8080/api/courses, `/actuator/health`
- PostgreSQL: localhost:5432

## Chạy từng phần khi phát triển

```bash
docker compose up -d db                 # chỉ chạy DB
cd Backend && mvn spring-boot:run       # http://localhost:8080
cd Web && npm install && npm run dev    # http://localhost:5173 (proxy /api -> 8080)
```

Mobile (cần Flutter SDK). Lần đầu tạo thư mục nền tảng android/ios:

```bash
cd Mobile
flutter create . --platforms=android,ios --project-name pps_smart_english
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080   # 10.0.2.2 = máy host từ Android emulator
```

## Test

```bash
cd Backend && mvn verify      # test dùng H2 chế độ PostgreSQL
cd Web && npm test
cd Mobile && flutter analyze && flutter test
```

## CI/CD (GitHub Actions)

- `backend-ci.yml`, `web-ci.yml`, `mobile-ci.yml`: chạy khi có thay đổi trong thư mục tương ứng (push `main` và pull request).
- `docker-publish.yml`: khi push `main` hoặc tag `v*`, build và đẩy image `backend` và `web` lên GHCR
  (`ghcr.io/<owner>/<repo>/backend|web`). Job `deploy` chỉ chạy khi đã đặt repository variable
  `DEPLOY_ENABLED=true` và các secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH`
  (server cần clone repo này tại `DEPLOY_PATH` và có file `.env`). Server chạy `docker-compose.prod.yml`.
