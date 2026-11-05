# PPS Smart English

| Thư mục | Công nghệ |
|---|---|
| `Web/` | React 19 + Vite + TypeScript |
| `Backend/` | Spring Boot 3.5, Java 21, Maven, Flyway |
| `Mobile/` | Flutter |
| DB | PostgreSQL 17 |

## Chạy bằng Docker Compose

```bash
cp .env.example .env     # đổi POSTGRES_PASSWORD, JWT_SECRET (>= 32 byte; giữ giá trị mẫu thì backend không khởi động)
docker compose up --build
```

- Web: http://localhost:3000 (nginx proxy `/api` sang backend)
- Backend: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432
- Redis: 127.0.0.1:6379 (bộ đếm rate limit; Redis lỗi thì backend vẫn chạy, giới hạn chuyển sang bộ nhớ)

## Chạy từng phần khi phát triển

```bash
docker compose up -d db redis            # chỉ chạy DB và Redis
cd Backend && JWT_SECRET=<chuỗi-32-byte-trở-lên> mvn spring-boot:run   # http://localhost:8080
cd Web && npm install && npm run dev    # http://localhost:5173 (proxy /api -> 8080)
```

Mobile (cần Flutter SDK). Lần đầu tạo thư mục nền tảng android/ios:

```bash
cd Mobile
flutter create . --platforms=android,ios --project-name pps_smart_english
flutter run
```

## Test

```bash
cd Backend && mvn verify      # test dùng H2 chế độ PostgreSQL
cd Web && npm test
cd Mobile && flutter analyze && flutter test
```

## CI/CD (GitHub Actions)

| Nhánh | Vai trò |
|---|---|
| `feat/*` | Làm tính năng, mở PR vào `develop` |
| `develop` | Chỉ tích hợp và chạy CI, không deploy |
| `main` | Staging: push thì build image `staging-*` và deploy |
| `production` | Bản cho người dùng: merge từ `main`, build image `prod-*` và deploy |
| `hotfix/*` | Sửa gấp, merge vào `production` rồi vào `main` |

- `backend-ci.yml`, `web-ci.yml`, `mobile-ci.yml`: chạy khi push `develop`, `main`, `production` và khi có pull request (chạy trên `ubuntu-latest`).
- `docker-publish.yml`: sau khi CI pass, build và đẩy image `backend`, `web` lên GHCR. Job `deploy` chạy trên self-hosted runner,
  chỉ khi repository variable `DEPLOY_ENABLED=true`; nó chép `docker-compose.prod.yml` vào `/opt/pps-smart-english/<môi trường>/docker-compose.yml`
  rồi `docker compose pull` và `up -d`.

Chuẩn bị máy chủ (dùng chung với pps-education, không có IP tĩnh nên phải dùng self-hosted runner):

1. Đăng ký self-hosted runner cho repo này với label `pps-smart-english`.
2. Tạo `/opt/pps-smart-english/staging/.env` và `/opt/pps-smart-english/production/.env` theo `.env.example`
   (staging: `APP_ENV=staging`, `WEB_PORT=8091`, `TAG=staging-latest`; production: `APP_ENV=production`, `WEB_PORT=8090`, `TAG=prod-latest`).
3. Trỏ hostname của cloudflared vào `127.0.0.1:WEB_PORT` của từng môi trường.
4. Tạo GitHub Environment `staging` và `production`, rồi đặt repository variable `DEPLOY_ENABLED=true`.
