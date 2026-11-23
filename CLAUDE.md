# PPS Smart English — quy tắc dự án

Nền tảng B2C bán khóa học tiếng Anh kết hợp LMS: khóa tự học và khóa có lớp (giáo viên, giao bài, điểm danh), mô hình khóa học → lớp. `Web/` React, `Backend/` Spring Boot (Java 21), `Mobile/` Flutter, DB PostgreSQL, chạy bằng Docker Compose. Lệnh build/test xem `README.md`.

## Tài liệu là nguồn

- `docs/PRD.md` và `docs/SRS.md` là nguồn yêu cầu duy nhất.
- **Không sửa, xóa hay bổ sung hai file này khi chưa có sự cho phép của chủ dự án.**
- Code mâu thuẫn hoặc thiếu so với tài liệu: dừng, báo lại, chờ quyết định. Không tự diễn giải.
- `docs/ba/` là bộ tài liệu BA phái sinh: `decisions.md` (quyết định D-xx đã đưa vào PRD v0.3 / SRS v1.2, câu hỏi BQ-xx còn mở), `use-cases.md` (luồng chi tiết), `user-stories.md`, `roadmap.md`, `lms-model.md` (mô hình dữ liệu khóa học → lớp, đề xuất), `lms-pivot-report.md` (báo cáo chuyển hướng LMS). Phần chạm BQ-xx chưa trả lời thì chưa code.
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

- **Backend:** Java 21, Spring Boot 3, Controller → Service → Repository, Flyway, PostgreSQL **17**, Redis (Pub/Sub kết quả AI, rate limit, cache; hàng đợi AI dùng bảng PostgreSQL, xem `docs/system-design.md` mục 9), Spring Security + JWT, MinIO (lưu media trên server, truy cập qua S3 API).
- **Web (giai đoạn 1):** React **19**, Vite, TypeScript, Tailwind **3.4** (làm thư viện FE chính), React Router 7, TanStack Query 5, React Hook Form + Zod, Vitest + Testing Library, Playwright. Responsive mobile-first, giao diện theo thiết kế Figma; phông Nunito (gói `@fontsource-variable/nunito`, có tiếng Việt); màu, bo góc, đổ bóng lấy từ token Figma (đã cấu hình trong `tailwind.config.js`, SRS 3.1). Thêm thư viện khi có tính năng đầu tiên cần dùng.
- **Mobile:** làm sau web khóa tự học, song song với phần khóa có lớp trên web (SRS 1.2, NFR-MOB-01, D-123), đủ tính năng học viên, không có màn quản trị, giáo viên, giáo vụ: Flutter, Riverpod, go_router, dio, flutter_secure_storage (token), SQLite (lưu tạm dữ liệu trên máy), Firebase Messaging, `record`. Dùng lại giao diện của web.
- **AI:** Haiku 5.5 qua Anthropic API cho phần lớn tác vụ. Speaking: GOPT + wav2vec2 + Whisper (Groq) như `pps-education` và `pps-speaking-engine`. Gọi qua interface `AiGradingProvider`.
- **Thanh toán:** interface `PaymentGateway`, theo cách `pps-education` làm; chưa kết nối cổng thật.
- **Thông báo:** Web Push (VAPID) cho web, FCM cho mobile.
- **Hạ tầng:** server riêng của công ty, Cloudflare Tunnel + nginx, đã có domain. Thêm Redis (chưa có). Docker Compose.

Các lệch trước đây (PostgreSQL 17, React 19, MinIO, chưa làm đăng ký SĐT/Apple, không tặng lượt AI khi đăng ký, màu và phông theo Figma) đã đưa vào PRD v0.2 / SRS v1.1 ngày 2026-11-13. Chuyển hướng LMS (D-97..D-124) đã đưa vào PRD v0.3 / SRS v1.2 ngày 2026-11-19. Hiện không còn mục lệch tài liệu.

## Nhánh

`feat/*` → `develop` (chỉ tích hợp, không deploy) → `main` (staging) → `production` (người dùng). `hotfix/*` vào `production` rồi vào `main`. Chi tiết và cách chuẩn bị server: `README.md` mục CI/CD.

## Duyệt trước khi lên production

- Mọi PR vào nhánh `production` (kể cả `hotfix/*`) phải qua `docs/production-checklist.md`: copy checklist vào mô tả PR, tick từng mục, ghi lý do với mục không áp dụng.
- Kiểm tra theo thứ tự P0, P1, P2. Một mục P0 không đạt thì không merge. P1 sửa trước khi deploy, hoặc có kế hoạch kèm người chịu trách nhiệm. P2 được ghi nợ kỹ thuật.
- Mục P0 liên quan đến tải, lỗi mạng hay dữ liệu lớn phải có test chứng minh, không chỉ đọc code.
- Khi viết code, áp dụng sẵn các yêu cầu P0 của checklist (timeout, giới hạn tài nguyên, phân quyền theo bản ghi, migration tương thích ngược...) để không dồn việc đến lúc duyệt.

## Theo dõi tiến độ trên GitHub Project

Chủ dự án theo dõi việc trên GitHub Project "PPS Smart English MVP" (`gh project ... 1 --owner langtuananh2424`, field `Status`: Todo / In Progress / Done). Phiên chính (Opus) cập nhật thường xuyên, không đợi được nhắc:

- Bắt đầu làm một việc thuộc issue nào thì chuyển issue đó sang **In Progress**.
- Xong một phần (backend, web, mobile) thì bình luận ngắn vào issue: phần đã xong, commit/PR, phần còn lại, điểm chưa kiểm chứng.
- Chỉ chuyển **Done** khi mọi phần của issue (backend, giao diện, test) đã xong và đã merge vào `develop`.
- Việc mới phát sinh mà chưa có issue: tạo issue, gắn vào project, ghi mã yêu cầu (FR/D-xx).
- Không đổi trạng thái hay tạo issue cho việc chưa bắt đầu hoặc chỉ là đề xuất chưa duyệt.

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
| `tester` | Opus 5.5 | Kiểm thử tổng thể và rà lỗ hổng bảo mật sau khi coder xong, theo `docs/testing-guide.md` |

- Opus gọi `researcher` trước khi quyết định; không tự đọc hàng loạt file.
- Opus giao `coder` bằng đặc tả rõ: mã FR, tác nhân, luồng, file liên quan, tiêu chí xong.
- `coder` thiếu thông tin thì trả câu hỏi về cho Opus, không tự đoán.
- Nhiều issue có thể giao song song cho nhiều `coder`; hai coder không sửa cùng file (chồng file thì dùng worktree riêng hoặc làm tuần tự).
- Khi mọi `coder` của đợt xong, Opus gom kết quả rồi giao **một** `tester` kiểm thử tổng thể cả đợt: danh sách issue/FR, file đã đổi, tiêu chí xong.
- `tester` chỉ viết test, không sửa code sản phẩm; lỗi và lỗ hổng trả về Opus để giao lại `coder`, sửa xong thì `tester` chạy lại.
- Định nghĩa agent ở `.claude/agents/`.
