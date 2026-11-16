# Checklist duyệt code trước khi lên Production

Nov 14, 2026 · @Lăng Anh

## Nguyên tắc cốt lõi và cách dùng

Một PR chỉ được lên production khi mọi tài nguyên đều có giới hạn, mọi thứ đã mượn đều được trả lại, và các tình huống xấu đã được kiểm chứng bằng test chứ không chỉ bằng việc đọc code.

Code do AI sinh thường đúng về logic nhưng thiếu giới hạn, vì AI không biết file nặng bao nhiêu, đối tác chậm cỡ nào hay heap có bao nhiêu MB. Khi review, với mỗi đoạn code hãy hỏi: *nếu dữ liệu to gấp 1.000 lần, hoặc bên kia chậm gấp 1.000 lần, thì cái gì sẽ phình ra vô hạn?*

| Mức | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| P0 | Bảo mật, mất dữ liệu, sập hệ thống, sai dữ liệu âm thầm | **Chặn merge** cho đến khi sửa xong |
| P1 | Khó vận hành, khó rollback, khó chẩn đoán sự cố | Sửa trước khi deploy, hoặc có kế hoạch rõ ràng kèm người chịu trách nhiệm |
| P2 | Hiệu năng truy vấn, chất lượng, bảo trì, tuân thủ | Có thể ghi nợ kỹ thuật (tech debt) và xử lý sau |

Cách dùng: copy checklist vào mô tả PR, tick từng mục, và ghi chú lý do với mục không áp dụng. Thiếu thời gian thì kiểm tra theo thứ tự P0, P1, P2.

## P0-A. Bảo mật, phân quyền, tiền bạc, dữ liệu

Một mục không đạt thì không được merge.

**Bảo mật**

- [ ] Không tắt kiểm tra SSL (`TrustAllCerts`, `NoopHostnameVerifier`) để "sửa lỗi certificate".
- [ ] Không hardcode secret (API key, mật khẩu DB); đọc từ biến môi trường hoặc Vault.
- [ ] Không nối chuỗi để tạo SQL; dùng tham số `?` (chống SQL injection).
- [ ] Đường dẫn file lấy từ input đã được chuẩn hóa (chống path traversal như `../../etc/passwd`).
- [ ] URL do người dùng truyền vào được kiểm tra whitelist (chống SSRF vào mạng nội bộ).
- [ ] Log không ghi nguyên request/response, không lộ token, mật khẩu, số điện thoại.
- [ ] Input và response từ đối tác có giới hạn kích thước.
- [ ] Dependency mới đã quét lỗ hổng (OWASP Dependency-Check, Snyk) và kiểm tra license (GPL/AGPL có thể gây vấn đề pháp lý).

**Phân quyền**

- [ ] Kiểm tra người dùng có quyền trên **đúng bản ghi đó**, không chỉ đã đăng nhập (chống IDOR: đổi `id` trên URL là xem được dữ liệu người khác).
- [ ] Thao tác nhạy cảm (nạp/rút tiền, đổi quyền, xóa dữ liệu) có audit trail: ai làm, lúc nào, từ đâu.

**Tiền và dữ liệu**

- [ ] Tiền dùng `BigDecimal` hoặc số nguyên theo đơn vị nhỏ nhất, không dùng `double`.
- [ ] Dữ liệu cá nhân (PII) được mã hóa khi lưu, che khi hiển thị và khi ghi log (tham chiếu Nghị định 13/2023/NĐ-CP), có chính sách thời gian lưu trữ và cách xóa theo yêu cầu.
- [ ] Migration DB tương thích ngược để deploy rolling (thêm cột trước, ghi cả hai, rồi mới bỏ cột cũ); bảng lớn dùng `CREATE INDEX CONCURRENTLY`, không khóa bảng.

## P0-B. Chống sập hệ thống: mọi tài nguyên đều có trần

Mỗi dòng dưới đây là một kiểu "phình vô hạn" mà code AI hay mắc. Tên phần in đậm là thứ cần kiểm tra.

| Khía cạnh | Dấu hiệu nguy hiểm | Yêu cầu để duyệt |
| --- | --- | --- |
| **Stream parsing** | `Files.readAllLines()`, `readAllBytes()`, `readValue(file, List.class)`, parse XML bằng DOM; cả file vài GB bị nạp vào RAM | Đọc theo luồng: `BufferedReader`, Jackson `JsonParser`, StAX. Xử lý theo batch (ví dụ 1.000 dòng/lần), bộ nhớ đi ngang dù file to bao nhiêu |
| **Timeout** | `new RestTemplate()` hoặc HttpClient mặc định (timeout vô hạn) | Đủ bốn loại: connect, read/socket, chờ lấy connection từ pool, và deadline tổng. Tổng `timeout × số lần thử + backoff` phải nằm gọn trong SLA của endpoint |
| **Backpressure** | `Executors.newFixedThreadPool` (queue không giới hạn); `flatMap` không truyền concurrency (mặc định 256); producer nhanh hơn consumer | Queue có trần (`ArrayBlockingQueue`) kèm `CallerRunsPolicy`; `flatMap(fn, 8)`; `Semaphore` hoặc bulkhead giới hạn số request đồng thời tới đối tác; `max.poll.records` với Kafka |
| **Thread starvation** | `.block()` trên event loop WebFlux; `parallelStream()` hoặc `CompletableFuture.supplyAsync()` không truyền executor (chạy trên `ForkJoinPool.commonPool` dùng chung) | I/O blocking chạy trên executor riêng có giới hạn (`Schedulers.boundedElastic()` hoặc pool riêng). Với virtual thread vẫn phải giới hạn số lời gọi xuống phía dưới |
| **Connection leak** | Tạo client mới mỗi lần gọi; không đóng hoặc không đọc hết response body; `Files.lines()` không đóng | Một client duy nhất khai báo dạng bean, pool cấu hình rõ ràng. Mọi tài nguyên nằm trong `try-with-resources`. Bật `leakDetectionThreshold` của HikariCP |

- [ ] Idle timeout phía client **ngắn hơn** keep-alive timeout của server và load balancer (tránh `Connection reset` do lấy phải kết nối cũ).
- [ ] Transaction DB ngắn, không bao quanh lời gọi HTTP hay xử lý nặng (tránh cạn pool HikariCP).
- [ ] Đã tắt `spring.jpa.open-in-view` nếu không cần.
- [ ] Cache có giới hạn kích thước và TTL; không dùng `static Map` tự viết không có cơ chế xóa.
- [ ] `ThreadLocal` luôn được `remove()` khi dùng trong thread pool.
- [ ] Không có `findAll()` không phân trang trên bảng lớn.

## P0-C. Chống sai dữ liệu âm thầm

Loại lỗi này không làm hệ thống sập nên rất khó phát hiện, thường chỉ lộ ra khi đối soát số liệu.

**Đồng thời (concurrency)**

- [ ] Singleton bean không chứa biến dùng chung không an toàn (`HashMap`, `ArrayList`, `SimpleDateFormat`); dùng `ConcurrentHashMap`, `DateTimeFormatter` hoặc đối tượng bất biến.
- [ ] Không có check-then-act (`if (!map.containsKey(k)) map.put(k, v)`); dùng `computeIfAbsent` hoặc `putIfAbsent`.
- [ ] Bộ đếm dùng `AtomicLong` hoặc `LongAdder`, không dùng `counter++` trên biến thường.
- [ ] Thứ tự lấy lock thống nhất ở mọi nơi (tránh deadlock), hoặc dùng `tryLock` có timeout.
- [ ] Tránh lost update: trừ kho hoặc cộng số dư dùng atomic update (`UPDATE ... SET qty = qty - 1 WHERE qty >= 1`), optimistic hay pessimistic locking phù hợp với mức tranh chấp.

**Gửi tin và ghi hai nơi (dual write)**

- [ ] Ghi DB và gửi Kafka/RabbitMQ dùng Transactional Outbox (CDC hoặc polling), không ghi hai nơi rời rạc.
- [ ] Consumer idempotent theo `event_id` (inbox pattern) vì outbox chỉ đảm bảo at-least-once.
- [ ] Callback từ cổng thanh toán có unique constraint trên mã giao dịch để không cộng tiền hai lần.

**Retry và xử lý lỗi**

- [ ] Chỉ retry request idempotent (GET, PUT); POST phải kèm `Idempotency-Key`.
- [ ] Chỉ retry lỗi tạm thời (timeout, 502, 503, 504, 429), **không** retry 400, 401, 404; tôn trọng `Retry-After`.
- [ ] Exponential backoff kèm jitter, giới hạn số lần thử; để ý retry lồng nhau giữa các tầng (3 tầng × 3 lần = 27 request).
- [ ] Có circuit breaker (Resilience4j) để fail fast khi đối tác lỗi liên tục.
- [ ] Không nuốt exception (`catch (Exception e) { e.printStackTrace(); return null; }`); bắt đúng loại, log kèm ngữ cảnh, rồi ném lại hoặc chuyển thành lỗi nghiệp vụ.
- [ ] Job chạy lâu có checkpoint, ghi idempotent (upsert theo khóa nghiệp vụ), dòng lỗi đưa ra dead letter thay vì làm hỏng cả job.

**Thời gian và trường hợp biên**

- [ ] Lưu thời gian dạng `Instant`/UTC, chỉ quy đổi múi giờ khi hiển thị (`LocalDateTime.now()` phụ thuộc múi giờ server, lệch 7 tiếng khi báo cáo theo ngày giờ Việt Nam).
- [ ] Đã xử lý danh sách rỗng, null, chuỗi có dấu tiếng Việt và emoji, số âm, trùng lặp, dữ liệu đến sai thứ tự.
- [ ] Parse JSON đối tác chịu được trường mới xuất hiện, trường null và JSON hỏng mà không trả `null` lây sang chỗ khác.

## P1. Vận hành và triển khai

Code chạy đúng nhưng khi hỏng không biết hỏng ở đâu, hoặc không quay lại được, thì chưa đạt.

**Quan sát (observability)**

- [ ] Có metric: latency (p95, p99), tỷ lệ lỗi, số request tới đối tác, trạng thái circuit breaker, kích thước hàng đợi (Micrometer + Prometheus).
- [ ] Theo dõi `hikaricp_connections_active` và `hikaricp_connections_pending`, heap, số lần GC, số thread.
- [ ] Log có cấu trúc kèm trace ID để lần theo một request qua nhiều service.
- [ ] Job dài log tiến độ (ví dụ "đã xử lý 1.200.000/5.000.000 dòng, 37 dòng lỗi").
- [ ] Có cảnh báo cho các chỉ số trên trước khi hệ thống sập.

**Triển khai và rollback**

- [ ] Rollback được: quay về phiên bản cũ mà không mất hay hỏng dữ liệu.
- [ ] Không đổi tên trường hay đổi kiểu dữ liệu của response đang có client dùng.
- [ ] Bật qua feature flag, chạy canary trên 5–10% lưu lượng rồi mới mở rộng.
- [ ] Graceful shutdown (`server.shutdown=graceful`): xử lý nốt request đang chạy trước khi pod tắt; job nền dừng được an toàn.
- [ ] Health check và readiness: chỉ nhận traffic khi đã kết nối DB xong và cache đã nạp.
- [ ] Heap phù hợp memory limit của container (`-XX:MaxRAMPercentage=75.0`) để không bị OOMKilled.
- [ ] Đã bật sẵn `-XX:+HeapDumpOnOutOfMemoryError`, GC log và có thể dùng JFR để chẩn đoán khi sự cố xảy ra.
- [ ] Cấu hình (timeout, kích thước batch, kích thước pool) chỉnh được qua tham số, không hardcode, và không có giá trị riêng của dev lọt lên production.

## P2. Hiệu năng truy vấn, chất lượng và bảo trì

Nhóm này có thể chấp nhận kèm ghi nợ kỹ thuật có kế hoạch xử lý, với điều kiện P0 và P1 đã đạt.

**Hiệu năng truy vấn**

- [ ] Không có N+1 query (JPA lazy loading trong vòng lặp); dùng `JOIN FETCH`, `@EntityGraph` hoặc truy vấn theo lô `IN (...)`.
- [ ] Không `SELECT *` và không `findAll()` không phân trang.
- [ ] Điều kiện lọc mới có index phù hợp; composite index đặt theo thứ tự Equality, Sort, Range; đã kiểm tra bằng `EXPLAIN (ANALYZE, BUFFERS)`.
- [ ] Phân trang trang sâu dùng keyset (`WHERE id > ? ORDER BY id LIMIT ?`) thay vì `OFFSET` lớn.
- [ ] Tham số truyền đúng kiểu cột, không bọc cột trong hàm khiến index không dùng được.

**Chất lượng và bảo trì**

- [ ] Tôi giải thích được từng dòng code; mọi API được gọi đều tồn tại trong đúng phiên bản thư viện đang dùng và không bị deprecated.
- [ ] Test có ý nghĩa: assert hành vi, trường hợp biên và trường hợp lỗi, không chỉ tăng coverage hay kiểm tra mock được gọi.
- [ ] Phù hợp convention của dự án (đặt tên, cấu trúc package, cách xử lý lỗi, cách log).
- [ ] Không thừa trừu tượng (interface, factory, lớp bọc không cần) và không thêm dependency vô cớ; ưu tiên thứ đã có trong JDK hoặc dự án.
- [ ] Không có dead code, comment sai lệch; đã đọc toàn bộ diff, kể cả những chỗ AI sửa lan ngoài yêu cầu.
- [ ] Code dùng đúng phiên bản Java, Spring và thư viện của dự án.
- [ ] Nếu code gọi LLM: nội dung từ người dùng hay tài liệu bên ngoài không được tự ý điều khiển hành động có quyền hạn (prompt injection).

**Hạ tầng mạng (nếu có gọi service khác)**

- [ ] Chỉ số `ip_local_port_range` và `somaxconn` được xem xét khi dự kiến số kết nối mới mỗi giây tăng đột biến.
- [ ] Pool kết nối (Keep-Alive) được bật, giới hạn `maxPerRoute` và `maxTotal` được nâng lên đủ dùng thay vì giữ mặc định rất thấp.

## Kiểm chứng bằng test và cách review code AI

Hy vọng đúng không đủ: mục P0 nào liên quan đến tải, lỗi mạng hay dữ liệu lớn đều cần một bài test chứng minh. Code AI cần được đối xử như code của một lập trình viên junior: giỏi viết happy path nhưng hay thiếu phần phòng thủ.

| Kịch bản | Công cụ | Tiêu chí đạt |
| --- | --- | --- |
| File rất lớn | Sinh file 5 GB, chạy với `-Xmx256m` | Không OOM, bộ nhớ đi ngang |
| Đối tác phản hồi chậm hoặc treo | WireMock (`withFixedDelay`), Toxiproxy | Timeout đúng hạn, không treo thread |
| Đối tác trả 500, 503, 429 | WireMock | Retry đúng số lần, có backoff, circuit breaker mở |
| Đối tác trả 400 | WireMock | **Không** retry |
| JSON hỏng hoặc thiếu trường | WireMock | Báo lỗi rõ ràng, không crash, không ra `null` |
| Tải cao kéo dài | k6, Gatling | Số connection, thread, heap ổn định, không tăng dần |
| DB và Kafka thật | Testcontainers | Batch, transaction, idempotency chạy đúng |
| Bảo mật và dependency | SonarQube, SpotBugs, OWASP Dependency-Check, Snyk | Không có lỗi mức High hoặc Critical |

**Quy trình khi review PR**

1. Đọc và hiểu toàn bộ code; dòng nào không giải thích được thì chưa merge.
2. Đối chiếu tài liệu chính thức với mọi khẳng định về đảm bảo ("tự động retry", "thread-safe"); AI có thể bịa hành vi.
3. Chạy checklist theo thứ tự P0, P1, P2.
4. Chạy các bài test xấu ở bảng trên cho những mục P0 liên quan.
5. Thử với dữ liệu thật đã ẩn danh, không chỉ dữ liệu mẫu trong prompt.
6. Load test, theo dõi thread, connection pool, heap trong lúc chạy.
7. Triển khai qua feature flag và canary, chỉ mở rộng khi dashboard ổn.

**Mẹo khi nhờ AI viết code:** nêu rõ ràng buộc ngay trong prompt, ví dụ "File có thể nặng 5 GB, heap 512 MB, Java 21, Spring Boot 3, API đối tác đôi khi treo hoặc trả 429, SLA của endpoint là 3 giây; cần có timeout, retry chỉ cho lỗi tạm thời và giới hạn đồng thời". AI viết tốt hơn hẳn khi biết các ràng buộc này, nhưng code sinh ra vẫn phải qua checklist.
