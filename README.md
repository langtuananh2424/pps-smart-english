# PPS Smart English

| Thư mục | Công nghệ |
|---|---|
| `Web/` | React 19 + Vite + TypeScript |
| `Backend/` | Spring Boot 3.5, Java 21, Maven, Flyway |
| `Mobile/` | Flutter |
| DB | PostgreSQL 17 |

Thiết kế giao diện: [Figma PPS Smart English](https://www.figma.com/design/CYuJRLBrMAXMwlLe43uZyy/PPS-Smart-English?m=auto&t=2MJ7jl8hjpoGj9tn-1). Dữ liệu trong Figma (tên khóa, đề, đáp án) là mẫu, chỉ lấy bố cục và hành vi.

## Chạy bằng Docker Compose

```bash
cp .env.example .env     # đổi POSTGRES_PASSWORD, JWT_SECRET (>= 32 byte; giữ giá trị mẫu thì backend không khởi động)
docker compose up --build
```

- Web: http://localhost:3000 (nginx proxy `/api` sang backend)
- Backend: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432
- Redis: 127.0.0.1:6379 (bộ đếm rate limit; Redis lỗi thì backend vẫn chạy, giới hạn chuyển sang bộ nhớ)

## Nhập từ điển Wiktionary (D-175)

Gợi ý nghĩa cho glossary bài giảng lấy từ bảng `dictionary_entries`. Nạp một lần từ file wiktextract JSONL (`.jsonl` hoặc `.jsonl.gz`, tải ở kaikki.org), chạy xong ứng dụng tự thoát; chạy lại thì xóa và nạp lại:

```bash
cd Backend && mvn spring-boot:run -Dspring-boot.run.arguments="--app.dictionary.import-file=/duong/dan/file.jsonl.gz --app.dictionary.import-edition=vi"
```

`import-edition`: `vi` (vi.wiktionary, nghĩa tiếng Việt trong glosses, mặc định) hoặc `en` (en.wiktionary, lấy bản dịch sang tiếng Việt, không có ví dụ).

## Chạy từng phần khi phát triển

```bash
docker compose up -d db redis            # chỉ chạy DB và Redis
cd Backend && JWT_SECRET=<chuỗi-32-byte-trở-lên> mvn spring-boot:run   # http://localhost:8080
cd Web && npm install && npm run dev    # http://localhost:5173 (proxy /api -> 8080)
```

Mobile (cần Flutter SDK **3.47.9**, cùng bản CI ghim trong `mobile-ci.yml`). Lần đầu tạo thư mục nền tảng android/ios:

```bash
cd Mobile
flutter create . --platforms=android,ios --project-name pps_smart_english
flutter run --dart-define API_BASE_URL=http://10.0.2.2:8080/api   # mặc định đã là giá trị này
```

Ghi chú Mobile (thư mục `android/` chưa có trong repo, thêm sau `flutter create`):

- Android cần quyền `<uses-permission android:name="android.permission.INTERNET"/>` trong `AndroidManifest.xml` và `android:usesCleartextTraffic="true"` ở thẻ `<application>` khi dev với `http://`; bỏ cờ này ở bản phát hành. `flutter_secure_storage` cần `minSdkVersion 23`.
- Thiết bị thật: đặt `API_BASE_URL` về IP máy dev hoặc domain staging.
- Phông Nunito chưa đóng gói (theme chỉ khai báo `fontFamily: 'Nunito'`); thêm file phông vào `assets/fonts/` và khai báo trong `pubspec.yaml` khi cần.

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
