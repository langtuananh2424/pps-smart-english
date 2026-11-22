# Đặc tả giao diện soạn và nhập bài tập (cho thiết kế)

Ngày: 2026-11-20 · Trạng thái: đề xuất để thiết kế, chưa duyệt · Nguồn: code backend hiện tại (`exercise/validation`, `CmsQuestionDtos`), `docs/SRS.md` (FR-EX-01..07, FR-ADM-02), `docs/ba/use-cases.md` mục 3.1, `docs/ba/decisions.md` (D-32, D-43, D-44, D-70). Không sửa PRD/SRS.

Mục tiêu: người của Phòng Đào tạo soạn và nhập bài tập dễ nhất có thể, không cần biết JSON. Dạng đơn giản tự làm giao diện; dạng phức tạp (ảnh, định dạng nhiều, công thức) dùng thư viện ngoài (xem mục 6).

---

## 1. Tổng hợp các dạng bài

Có ba lớp, cần phân biệt để không nhầm khi thiết kế.

### 1.1. Mười kiểu dữ liệu đã có backend (soạn được ngay)

| Mã (`questions.type`) | Tên hiển thị đề xuất | Cách chấm | Dữ liệu đề (`payload`) | Đáp án (`answerKey` / `choices`) |
|---|---|---|---|---|
| `CONNECTIVE_MCQ` | Trắc nghiệm một đáp án | Tự động | không có | `choices`: nhãn, nội dung, đúng/sai, giải thích riêng từng lựa chọn; 2–10 lựa chọn, **đúng 1** lựa chọn đúng |
| `CONNECTIVE_MATCH` | Ghép hai vế | Tự động | `left[]`, `right[]` (mỗi phần tử có id + chữ) | `pairs`: mỗi vế trái một vế phải |
| `CONNECTIVE_CLOZE` | Điền từ vào chỗ trống | Tự động | `text` có chỗ trống `{id}`, `blanks[]`, tên chỗ trống (tùy chọn) | `blanks`: mỗi chỗ trống có danh sách đáp án chấp nhận |
| `CONNECTIVE_REWRITE` | Viết lại với từ nối | Luật, không khớp luật thì AI | `sentence`, `requiredWord` | `acceptedAnswers[]` (không bắt buộc) |
| `GRAMMAR_IDENTIFY` | Nhận diện cấu trúc | Tự động, chấm từng phần (D-44) | `tokens[]` (từ của câu) | `spans[]`: `start`, `end`, `element` (một trong 8 yếu tố ngữ pháp) |
| `GRAMMAR_SPOT_FIX` | Tìm và sửa lỗi | Tự động | `tokens[]` | `position` (từ sai), `corrections[]` (cách sửa chấp nhận) |
| `GRAMMAR_BUILD` | Xếp khối thành câu | Tự động, chấm cả câu | `blocks[]` (id + chữ) | `order[]`: thứ tự đúng của các id |
| `GRAMMAR_REWRITE` | Viết lại theo cấu trúc | Luật, không khớp luật thì AI | `sentence`, `requiredElement` | `acceptedAnswers[]` (không bắt buộc) |
| `WRITING` | Viết tự luận | AI chấm, trừ lượt | `task`, `requiredWords`, `ieltsTask` (TASK1/TASK2, tùy chọn) | không có |
| `SPEAKING` | Nói | AI chấm, trừ lượt | `part` (1–3), `prompt`, `cueCard[]` (chỉ part 2), `maxSeconds` (10–180) | không có |

Tám yếu tố ngữ pháp (dùng cho IDENTIFY và GRAMMAR_REWRITE): mệnh đề quan hệ tính ngữ, câu đảo ngữ, câu bị động, chủ ngữ giả, mệnh đề phân từ, mệnh đề danh từ, "seem / be likely / tend to", các cấu trúc quen thuộc khác. Mỗi yếu tố có màu (cột `color_hex`, chờ thiết kế).

### 1.2. Dữ liệu đi kèm mọi câu hỏi

| Thông tin | Ghi chú |
|---|---|
| Đề bài (`prompt`) | Chữ thuần, tối đa 5000 ký tự |
| Dòng hướng dẫn (`instruction`) | Tối đa 300 ký tự, hiện phía trên câu (ví dụ "Chọn đáp án đúng") |
| Giải thích (`explanation`) | Tối đa 5000 ký tự, hiện sau khi chấm |
| Thẻ yếu tố ngữ pháp | Tối đa 8 thẻ |
| Kỹ năng (`skill`) | LISTENING / READING / GRAMMAR, chỉ dùng cho đề kiểm tra đầu vào |
| Thứ tự (`sortOrder`) | Kéo thả để đổi |
| Gợi ý Cam (`hints`) | Nhiều gợi ý mỗi câu, mỗi gợi ý có phần ngắn và phần chi tiết (popup) |
| Phiên bản đáp án | Tự tăng khi đáp án thật sự đổi; nếu đã có bài nộp thì báo "cần chấm lại" |

### 1.3. Thuộc tính của bộ câu hỏi (bank)

Tên, Topic, bài giảng gắn kèm (tùy chọn), chế độ **Luyện tập** (được tạm dừng) hoặc **Thi** (không tạm dừng), giới hạn thời gian (giây), cờ học thử, trạng thái Nháp / Đã xuất bản, mục đích **Khóa học** hoặc **Kiểm tra đầu vào** (đề đầu vào bắt buộc là Thi và có giờ), bộ FAQ cho Cam (cặp hỏi–đáp).

### 1.4. Phần Figma và SRS yêu cầu nhưng backend CHƯA có

- **39 dạng "Luyện tập PPS"** (Figma 10.01–10.39, `use-cases.md` mục 3.1). Tài liệu ghi 39 dạng phần lớn là **cách hiển thị** của khoảng 8 kiểu dữ liệu của `pps-education`. Backend mới có 10 kiểu ở bảng 1.1.
- Các dạng chưa có kiểu dữ liệu riêng: chọn nhiều đáp án, đúng/sai, điền từ theo nhóm, chọn từ trong hộp, sắp xếp chữ cái, chép chính tả, video phản xạ, nhóm câu độc lập.
- **Chưa có chỗ lưu ảnh, âm thanh, đoạn đọc** trong bảng `questions`: chưa làm được đề ảnh, đề nghe, đọc hiểu (đoạn văn dùng chung cho nhóm câu).
- **IELTS Reading (15 dạng) và Listening (11 dạng)** (FR-EX-05) chưa có. Writing Task 1/2 và Speaking Part 1–3 đã có.

→ Cần chủ dự án chốt thứ tự làm các dạng còn thiếu (mục 7).

---

## 2. Nguyên tắc giao diện

1. **Một khung chung, một biểu mẫu riêng cho mỗi dạng.** Người soạn không bao giờ thấy JSON.
2. **Xem trước ngay bên cạnh**, đúng như học viên thấy, kèm nút "Làm thử" (nhập đáp án và xem hệ thống chấm ra sao).
3. **Báo lỗi tại ô**, bằng chính thông báo của backend (ví dụ "Phải có đúng 1 lựa chọn đúng"), không chờ bấm lưu mới biết.
4. **Soạn nhanh bằng bàn phím**: Enter thêm dòng, Tab sang ô kế, phím tắt chèn chỗ trống.
5. **Nhập hàng loạt luôn có bước xem trước và sửa tại chỗ** trước khi ghi; có lỗi thì không ghi dòng nào sai.
6. Quyền: người có `cms.content` (Quản trị viên, Biên tập viên).

---

## 3. Khung màn hình soạn bộ câu hỏi

Bố cục ba cột:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Topic ▸ Tên bộ câu hỏi      [Nháp ▾]   [Nhập hàng loạt]  [Xem trước]  [Lưu]│
├───────────────┬─────────────────────────────────────┬────────────────────┤
│ Danh sách câu │ Biểu mẫu soạn (theo dạng)           │ Xem trước học viên │
│ 1 ▪ Trắc nghiệm│ Dạng: [Trắc nghiệm ▾]              │  (khung điện thoại │
│ 2 ▪ Điền từ    │ Hướng dẫn: ...                      │   / máy tính)      │
│ 3 ▪ Nối câu  ⚠ │ Đề bài: ...                         │                    │
│ + Thêm câu     │ ... phần riêng của dạng ...         │ [Làm thử]          │
│ (kéo để đổi    │ Giải thích · Thẻ ngữ pháp · Gợi ý Cam│                    │
│  thứ tự)       │ [Nhân bản] [Xóa]                    │                    │
└───────────────┴─────────────────────────────────────┴────────────────────┘
```

- **Thanh đầu:** tên bộ, trạng thái Nháp/Đã xuất bản (xuất bản khi mọi câu hợp lệ), nút nhập hàng loạt, xem trước cả bộ.
- **Cột trái:** danh sách câu có số thứ tự, biểu tượng dạng, dấu ⚠ khi câu chưa hợp lệ, tìm kiếm và lọc theo dạng, kéo thả đổi thứ tự, nhân bản câu, chọn nhiều để xóa hoặc gắn thẻ hàng loạt.
- **Cột giữa:** biểu mẫu theo dạng (mục 4). Phần chung cuối biểu mẫu: giải thích, thẻ yếu tố ngữ pháp (chọn nhiều, tối đa 8), gợi ý Cam (thêm nhiều gợi ý, mỗi gợi ý có ô ngắn và ô chi tiết).
- **Cột phải:** xem trước đúng giao diện học viên; nút **Làm thử** hiện kết quả chấm và giải thích.
- **Chuyển đổi dạng:** đổi dạng của một câu thì hỏi xác nhận vì dữ liệu riêng của dạng cũ sẽ mất; giữ lại đề bài.
- **Cảnh báo đáp án đã có bài nộp:** nếu câu có bài nộp và đáp án thay đổi, hiện thanh vàng "Đã có học viên làm câu này. Đổi đáp án cần chấm lại" kèm nút tới màn Chấm lại.
- **Trạng thái:** đang lưu, đã lưu, lỗi lưu (giữ nguyên nội dung), xung đột khi người khác vừa sửa.

---

## 4. Biểu mẫu soạn theo từng dạng

Quy ước: ô **đỏ** là bắt buộc; lỗi hiện dưới ô.

### 4.1. Trắc nghiệm một đáp án
- Đề bài.
- Danh sách lựa chọn: mỗi dòng gồm **nhãn** (A, B, C… tự đánh, sửa được, tối đa 10 ký tự, không trùng), **nội dung**, **nút chọn đúng** (radio, chỉ một), ô **giải thích riêng** (thu gọn).
- Thêm / xóa / kéo đổi lựa chọn; tối thiểu 2, tối đa 10.
- Lỗi: "Cần ít nhất 2 lựa chọn", "Phải có đúng 1 lựa chọn đúng", "Nhãn bị lặp".
- Gợi ý nhanh: dán nhiều dòng vào ô lựa chọn thì tự tách thành nhiều lựa chọn.

### 4.2. Ghép hai vế
- Hai cột: **Vế trái** và **Vế phải**, mỗi vế một ô chữ, thêm dòng bằng Enter.
- Nối cặp: mỗi vế trái có một menu chọn vế phải (hoặc kéo dây nối giữa hai cột). Vế phải có thể thừa (làm nhiễu).
- Mỗi vế trái phải có đúng một cặp; xem trước hiện bảng cặp đã nối.
- Lỗi: "Phải có đúng một cặp cho mỗi vế trái".

### 4.3. Điền từ vào chỗ trống
- Ô văn bản lớn. Nút **[Chèn chỗ trống]** (phím tắt) chèn một chip `⟦1⟧` tại con trỏ; chỗ trống tự đánh số.
- Dưới ô văn bản, mỗi chỗ trống có một thẻ: **đáp án chấp nhận** (nhập nhiều, mỗi đáp án là một chip, Enter để thêm), **tên chỗ trống** (tùy chọn, tối đa 100 ký tự).
- Xóa chip trong văn bản thì thẻ tương ứng biến mất, và ngược lại.
- Lỗi: "Mỗi chỗ trống phải có ít nhất một đáp án chấp nhận".

### 4.4. Viết lại với từ nối
- Ô **câu gốc**; ô **từ nối bắt buộc** (một từ).
- **Đáp án mẫu** (không bắt buộc): danh sách chip. Nếu để trống, hiện ghi chú "Không có đáp án mẫu: câu trả lời không khớp luật sẽ do AI chấm và trừ lượt".

### 4.5. Viết lại theo cấu trúc
- Ô câu gốc; **yếu tố ngữ pháp bắt buộc** (menu 8 yếu tố, hiện màu); đáp án mẫu như 4.4.

### 4.6. Nhận diện cấu trúc
- Gõ hoặc dán câu → hệ thống **tự tách thành các từ** hiện thành chip; sửa được (gộp / tách từ).
- Chọn cụm: bấm chip đầu rồi chip cuối (hoặc kéo qua nhiều chip) → hiện menu chọn **yếu tố ngữ pháp** → cụm được gạch chân đúng màu yếu tố.
- Cho phép cụm lồng nhau (gạch chân nhiều lớp, tối đa 3 lớp, D-44). Cụm ngắt quãng không hỗ trợ (D-44).
- Bên dưới là danh sách cụm đã chọn: "cụm → nhãn", xóa từng cụm.
- Xem trước hiện số cụm cần tìm. Điểm chấm từng phần.
- Lỗi: "Cần ít nhất 1 cụm".

### 4.7. Tìm và sửa lỗi
- Câu tách thành chip như 4.6. Bấm **một** chip để đánh dấu là từ sai (tô đỏ).
- Danh sách **cách sửa chấp nhận** (chip, ít nhất 1).

### 4.8. Xếp khối thành câu
- Cách 1: gõ câu đúng, bấm **[Tách khối]** → tách theo từ, sửa lại (gộp nhiều từ thành một khối, tách khối).
- Cách 2: thêm từng khối thủ công.
- Kéo để sắp thứ tự **đúng**; nút **[Xáo trộn]** để xem học viên sẽ thấy gì. Tối thiểu 2 khối.

### 4.9. Viết tự luận
- Đề bài (`task`, ô chữ lớn); **số từ yêu cầu** (số > 0); chọn **IELTS Task 1 / Task 2 / Không** (quyết định số lượt AI trừ: Task 1 trừ 2, Task 2 trừ 3, D-32).
- Ghi chú cố định: "Chấm bằng AI, trừ lượt của học viên."

### 4.10. Nói
- Chọn **Part 1 / 2 / 3** (nút phân đoạn). Ô **đề** (tối đa 2000 ký tự).
- **Cue card** (chỉ Part 2): danh sách gạch đầu dòng.
- **Thời gian nói tối đa** (10–180 giây); để trống thì dùng mặc định theo Part (D-43).

---

## 5. Nhập hàng loạt

Một đường vào chung là nút **[Nhập hàng loạt]** ở thanh đầu bộ câu hỏi, mở trình hướng dẫn 4 bước. Ba cách nhập, từ dễ đến khó:

### 5.1. Ba cách

| Cách | Hợp với | Ghi chú |
|---|---|---|
| **A. Dán nhanh** (soạn trong ô văn bản, tự phân tích tức thì) | Trắc nghiệm, nối, điền từ, xếp khối cỡ vài chục câu | Thân thiện nhất, không cần mở Excel |
| **B. Bảng tính** (tải mẫu Excel, điền, tải lên; hoặc dán từ Excel vào bảng trên web) | Cả trăm câu, người quen Excel | Mỗi dạng một sheet mẫu |
| **C. Từ file Word** (có ảnh, định dạng) | Đề phức tạp | Làm sau, dùng thư viện ngoài, xem mục 6 |

### 5.2. Cách A: Dán nhanh
Một ô văn bản bên trái, kết quả phân tích bên phải, cập nhật khi gõ. Cú pháp mẫu (hiện sẵn ví dụ trong ô, có nút "Xem hướng dẫn"):

```
# Trắc nghiệm
Q: She was tired ___ she kept working.
A) because
*B) although
C) so
Giải thích: "although" diễn tả sự tương phản.
Thẻ: RELATIVE_CLAUSE

# Điền từ
Q: He stayed home {1} it rained. {2} he was ill, he went out.
1: because | since
2: Although | Though

# Ghép
Q: Nối hai vế
cause -> effect
...

# Xếp khối
Q: She / has / lived / here / since / 2010
```

Bên phải mỗi câu đã phân tích hiện: dạng tự nhận, xem trước, trạng thái (✓ hợp lệ / ⚠ lỗi + lý do + số dòng). Dòng lỗi tô đỏ trong ô văn bản.

### 5.3. Cách B: Bảng tính, cột theo từng dạng

| Dạng | Cột |
|---|---|
| Trắc nghiệm | Đề · Lựa chọn A…J · Đáp án đúng (A–J) · Giải thích · Thẻ |
| Ghép hai vế | Đề · Vế trái 1…n · Vế phải 1…n · Cặp (ví dụ `1-3, 2-1`) · Giải thích |
| Điền từ | Đề (chỗ trống ghi `{1}`, `{2}`) · Đáp án chỗ 1 · Đáp án chỗ 2… (nhiều đáp án ngăn bằng `|`) · Giải thích |
| Xếp khối | Các khối theo thứ tự đúng ngăn bằng `/` |
| Nhận diện cấu trúc | Câu · Cụm (ví dụ `3-6:PASSIVE; 8-9:INVERSION`) |
| Tìm và sửa lỗi | Câu · Vị trí từ sai · Cách sửa (ngăn bằng `|`) |
| Viết lại (2 loại) | Câu gốc · Từ nối / Yếu tố · Đáp án mẫu |
| Viết, Nói | Đề · Số từ / Part · Thời gian |

Có nút **Tải mẫu** (file đã điền sẵn 2–3 dòng ví dụ mỗi dạng). Trên web, cho **dán trực tiếp từ Excel vào bảng** (không bắt buộc tải lên file).

### 5.4. Luồng 4 bước (cả A và B)

1. **Chọn cách và dạng:** chọn cách nhập, tải mẫu, chọn bộ câu hỏi đích (hoặc tạo bộ mới), chế độ nhập: **Thêm vào cuối** (mặc định) hoặc **Thay thế** (chỉ khi bộ còn Nháp).
2. **Tải lên / Dán** nội dung.
3. **Xem trước và sửa:** bảng một dòng một câu: số dòng, dạng, đề rút gọn, trạng thái. Bấm dòng mở biểu mẫu của mục 4 ngay tại chỗ để sửa. Lọc nhanh "Chỉ dòng lỗi". Nút **[Bỏ dòng này]**. Banner tổng kết "42 hợp lệ, 3 lỗi". Phát hiện **câu trùng** (cùng đề trong bộ) và gắn cờ cảnh báo.
4. **Xác nhận:** nút **[Nhập N câu hợp lệ]**. Có hai lựa chọn: *chỉ nhập khi tất cả hợp lệ* (mặc định, không ghi gì nếu còn lỗi) hoặc *bỏ qua dòng lỗi*. Sau khi xong hiện: số câu đã thêm, danh sách dòng bị bỏ kèm lý do, nút **Tải báo cáo lỗi (Excel)**, nút **Mở bộ câu hỏi**.

Đề xuất thêm: **Lịch sử nhập** của từng bộ (ngày, người nhập, số câu) và nút **Hoàn tác lần nhập** khi các câu vừa nhập chưa có bài nộp.

### 5.5. Quy tắc kiểm tra khi nhập
Giống hệt quy tắc khi soạn tay (cùng một bộ luật ở backend), nên một dòng không bao giờ lọt vào mà soạn tay không lưu được. Mọi lỗi nêu số dòng, tên cột, lý do bằng tiếng Việt.

---

## 6. Phần phức tạp (ảnh, định dạng, công thức)

Chưa thiết kế chi tiết, cần chốt hướng trước (mục 7). Hướng đã bàn:

- Soạn một câu: trình soạn rich text (ứng viên: CKEditor 5 cần giấy phép thương mại cho sản phẩm đóng nguồn; TipTap lõi MIT). Cột giữa của mục 3 đổi ô "Đề bài" thành trình soạn này; chèn ảnh có nút tải lên.
- Nhập hàng loạt có định dạng: file **Word** theo mẫu có sẵn kiểu đoạn ("Câu hỏi", "Lựa chọn", "Đáp án"), đổi sang HTML bằng thư viện (ứng viên: mammoth), ảnh đẩy lên kho lưu trữ, lọc HTML ở máy chủ. Vẫn qua bước xem trước và sửa như 5.4.
- Cần thay đổi dữ liệu: thêm cột nội dung giàu định dạng, ảnh, âm thanh, đoạn đọc cho `questions`.

---

## 7. Việc cần chủ dự án quyết định

1. **Dạng bài làm trước:** làm đủ giao diện cho 10 kiểu hiện có trước, hay thêm ngay các dạng còn thiếu (chọn nhiều đáp án, đúng/sai, chọn từ trong hộp, nghe, đọc hiểu, IELTS Reading/Listening)? Mỗi dạng thêm cần backend và schema mới.
2. **Ảnh và âm thanh trong câu hỏi:** cần ngay trong đợt này không? Nếu có, backend phải đổi trước giao diện.
3. **CKEditor hay TipTap** (hoặc chưa cần rich text ở đợt đầu): kéo theo việc có mua giấy phép và sửa SRS mục công nghệ.
4. **Đối tượng nhập chính:** Phòng Đào tạo quen Excel hay Word? Quyết định ưu tiên cách A, B hay C.
5. **Hoàn tác lần nhập** và **phát hiện câu trùng:** có đưa vào đợt đầu không (cần backend).
6. Hai câu hỏi cũ chưa chốt (`use-cases.md`): "Nhận diện cấu trúc" và "Xếp khối" ứng với dạng Figma nào (10.01, 10.10?).
