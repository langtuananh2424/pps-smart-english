# PPS Smart English — quy tắc dự án

Nền tảng B2C bán khóa học và luyện tập tiếng Anh. `Web/` React, `Backend/` Spring Boot (Java 21), `Mobile/` Flutter, DB PostgreSQL, chạy bằng Docker Compose. Lệnh build/test xem `README.md`.

## Tài liệu là nguồn

- `docs/PRD.md` và `docs/SRS.md` là nguồn yêu cầu duy nhất.
- **Không sửa, xóa hay bổ sung hai file này khi chưa có sự cho phép của chủ dự án.**
- Code mâu thuẫn hoặc thiếu so với tài liệu: dừng, báo lại, chờ quyết định. Không tự diễn giải.
- `docs/system-design.md` là thiết kế phái sinh (kiến trúc, dữ liệu, luồng, câu hỏi mở `[Q-xx]`). Lệch PRD/SRS thì PRD/SRS đúng. Phần chạm câu hỏi mở chưa trả lời thì chưa code.

## Khi thêm hoặc sửa tính năng

- Trước khi code phải nêu rõ: mã yêu cầu (FR/NFR), tác nhân, luồng chính và luồng lỗi, dữ liệu vào/ra.
- Thiếu thông tin trong tài liệu: hỏi, không đoán mò. Không tự bịa dữ liệu, tên, số liệu nghiệp vụ.
- Chưa rõ thuật ngữ trong yêu cầu của chủ dự án: hỏi lại cho chắc rồi mới làm.
- Một thay đổi chỉ phục vụ một yêu cầu đã nêu.

## Về code

- Tuân thủ skill `ponytail`.
- Tuân thủ SOLID; backend theo lớp Controller → Service → Repository (SRS 6.1).
- Tận dụng tối đa design pattern: dùng được thì áp dụng luôn để dễ bảo trì và cập nhật. Rule này được ưu tiên hơn phần hạn chế abstraction của `ponytail`.
- Giữ code base gọn: mỗi file khoảng 700 dòng trở xuống; vượt thì tách.
- Comment ngắn, đúng trọng tâm, chỉ giải thích "vì sao" khi code không tự nói lên được.

## Tech stack đã chốt

Tài liệu (SRS 2.1) là nguồn; mục nào lệch tài liệu ghi rõ ở "Chờ quyết định".

- **Backend:** Java 21, Spring Boot 3, Controller → Service → Repository, Flyway, PostgreSQL **17**, Redis (Pub/Sub kết quả AI, rate limit, cache; hàng đợi AI dùng bảng PostgreSQL, xem `docs/system-design.md` mục 9), Spring Security + JWT, Cloudflare R2.
- **Web (giai đoạn 1):** React **19**, Vite, TypeScript, Tailwind **3.4** (làm thư viện FE chính), React Router 7, TanStack Query 5, React Hook Form + Zod, Vitest + Testing Library, Playwright. Responsive mobile-first, giao diện theo thiết kế Figma; phông Nunito (gói `@fontsource-variable/nunito`, có tiếng Việt); màu, bo góc, đổ bóng lấy từ token Figma (đã cấu hình trong `tailwind.config.js`, lệch SRS 3.1 và 8.3 do chủ dự án chọn). Thêm thư viện khi có tính năng đầu tiên cần dùng.
- **Mobile:** chỉ giữ khung `Mobile/`, chưa phát triển. Khi bắt đầu: Flutter, Riverpod, go_router, dio, flutter_secure_storage, Firebase Messaging, `record`. Dùng lại giao diện của web.
- **AI:** Haiku 5.5 qua Anthropic API cho phần lớn tác vụ. Speaking: GOPT + wav2vec2 + Whisper (Groq) như `pps-education` và `pps-speaking-engine`. Gọi qua interface `AiGradingProvider`.
- **Thanh toán:** interface `PaymentGateway`, theo cách `pps-education` làm; chưa kết nối cổng thật.
- **Thông báo:** Web Push (VAPID) cho web, FCM cho mobile.
- **Hạ tầng:** server riêng của công ty, Cloudflare Tunnel + nginx, đã có domain. Thêm Redis (chưa có). Docker Compose.

Lệch SRS 2.1 đã được chủ dự án chọn: PostgreSQL 17 (SRS: 16), React 19 (SRS: 18). Lệch SRS FR-AUTH-01: MVP chưa làm đăng ký bằng số điện thoại và Apple Sign-in. Lệch SRS 2.2, FR-AUTH-01 và PRD FR-AUTH-02, 4.1: tài khoản mới không được tặng 3 lượt AI; lượt AI chỉ cấp khi mua khóa. SRS chưa được cập nhật; chỉ sửa khi chủ dự án cho phép.

## Nhánh

`feat/*` → `develop` (chỉ tích hợp, không deploy) → `main` (staging) → `production` (người dùng). `hotfix/*` vào `production` rồi vào `main`. Chi tiết và cách chuẩn bị server: `README.md` mục CI/CD.

## Commit

Theo Conventional Commits: `type(scope): subject`

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`.
- `scope`: `web`, `backend`, `mobile`, `db`, `infra`, `docs`; thay đổi nhiều phần thì bỏ scope.
- `subject`: tiếng Anh, thức mệnh lệnh, viết thường, không dấu chấm cuối, tối đa 72 ký tự.
- Mỗi commit một thay đổi logic; không gộp tính năng với refactor.
- Tính năng ghi mã yêu cầu ở footer: `Refs: FR-AUTH-01`. Thay đổi phá vỡ: thêm `!` sau scope và `BREAKING CHANGE:` ở footer.
- Thay đổi `docs/PRD.md`, `docs/SRS.md` chỉ commit khi chủ dự án đã cho phép, dùng `docs(docs):`.
- Chỉ commit và push khi chủ dự án yêu cầu.

## Phân vai agent

| Vai | Model | Việc |
|---|---|---|
| Đầu não (phiên chính) | Opus 5.5 | Thiết kế luồng, đề xuất, chia việc, duyệt kết quả |
| `researcher` | Haiku 5.5 | Tìm, đọc, tổng hợp tài liệu/code rồi gửi Opus |
| `coder` | Sonnet 5.5 | Viết code theo đặc tả Opus giao |

- Opus gọi `researcher` trước khi quyết định; không tự đọc hàng loạt file.
- Opus giao `coder` bằng đặc tả rõ: mã FR, tác nhân, luồng, file liên quan, tiêu chí xong.
- `coder` thiếu thông tin thì trả câu hỏi về cho Opus, không tự đoán.
- Định nghĩa agent ở `.claude/agents/`.
