# Kiểu câu mới theo D-154, D-157 (đặc tả dữ liệu)

Phái sinh từ docs/ba/decisions.md D-154, D-157, D-158, D-159 và Figma (đọc ngày 2026-11-26). PRD/SRS thắng khi lệch.

## Nguyên tắc

1. Mỗi dạng một giá trị `questions.type`, có validator, grader, renderer web, form CMS riêng. Phần giống nhau dùng chung ở mức code (lớp cơ sở, hàm tiện ích), không ở mức kiểu.
2. **Một số câu IELTS = một câu hỏi = tối đa 1 điểm** (credit 0..1 như hiện có). Không có câu nhiều điểm.
3. Phần dùng chung của dạng (bảng, ghi chú, sơ đồ, phiếu, danh sách heading/lựa chọn, hộp từ) nằm ở **nhóm câu**: cột mới `question_groups.layout` (JSON, null được). Câu trong nhóm tham chiếu chỗ của mình trong layout bằng `payload.slot` (chuỗi). Layout cùng nhóm phải khớp kiểu câu của nhóm: mọi câu trong nhóm có layout cùng một kiểu.
4. So khớp chữ gõ: bỏ khoảng trắng thừa, không phân biệt hoa thường; có danh sách đáp án chấp nhận; `maxWords` tùy chọn (đếm theo \\s+, cả web và backend cùng một luật). Dạng chép chính tả còn bỏ dấu câu cuối và dấu câu thường (. , ! ? ; :) trước khi so.
5. Kiểu cũ giữ nguyên, vẫn dùng cho dạng Luyện tập PPS đã có.

## Luyện tập PPS

| Kiểu | Figma | Payload | answerKey | Chấm |
|---|---|---|---|---|
| `LETTER_ORDER` | 10.11 | `{letters:[{id,ch}]}` (thứ tự hiển thị đã xáo do người soạn lưu; CMS có nút xáo) | `{word}` | Ghép chữ theo thứ tự học viên chọn, so không phân biệt hoa thường. Trả lời: `{order:[id]}` |
| `DICTATION` | 10.34 | `{maxWords?}`; audio ở nhóm câu (bắt buộc nhóm có audio) | `{accepted:[text]}` | Luật 4 kèm bỏ dấu câu. Trả lời `{text}` |
| `LISTEN_FORM` | 10.22 | `{slot, maxWords?}`; nhóm có audio và layout `{kind:'FORM', rows:[{label, slot?, value?}]}` (dòng có value là dòng điền sẵn) | `{accepted:[text]}` | Luật 4. Trả lời `{text}` |

## IELTS Reading / Listening (tiền tố `IELTS_`)

| Kiểu | Payload câu | Layout nhóm | answerKey | Trả lời |
|---|---|---|---|---|
| `IELTS_TFNG` | `{statement}` | — | `{value: TRUE\|FALSE\|NOT_GIVEN}` | `{value}` |
| `IELTS_YNNG` | `{statement}` | — | `{value: YES\|NO\|NOT_GIVEN}` | `{value}` |
| `IELTS_MATCH_INFO` | `{statement}` | `{kind:'OPTIONS', title?, options:[{id,text}], reuse:true}` (đoạn A–H) | `{option}` | `{option}` |
| `IELTS_MATCH_HEADINGS` | `{paragraph}` (vd "Paragraph B") | `{kind:'OPTIONS', title:'List of headings', options:[{id:'i',text}], reuse:false}` | `{option}` | `{option}` |
| `IELTS_MATCH_FEATURES` | `{statement}` | `{kind:'OPTIONS', title, options, reuse}` | `{option}` | `{option}` |
| `IELTS_MATCH_ENDINGS` | `{stem}` (nửa đầu câu) | `{kind:'OPTIONS', options, reuse:false}` | `{option}` | `{option}` |
| `IELTS_MATCHING` | `{statement}` (Listening) | `{kind:'OPTIONS', title, options, reuse}` | `{option}` | `{option}` |
| `IELTS_SENTENCE` | `{before, after, maxWords?}` | — | `{accepted}` | `{text}` |
| `IELTS_SUMMARY` | `{slot, maxWords?}` | `{kind:'TEXT', html}` với chỗ trống `{slot}` | `{accepted}` | `{text}` |
| `IELTS_SUMMARY_BANK` | `{slot}` | `{kind:'TEXT_BANK', html, words:[{id,text}], reuse}` | `{word}` | `{word}` |
| `IELTS_NOTE` | `{slot, maxWords?}` | `{kind:'NOTE', title?, lines:[{html}]}` chỗ trống `{slot}` trong html | `{accepted}` | `{text}` |
| `IELTS_TABLE` | `{slot, maxWords?}` | `{kind:'TABLE', title?, header:[text], rows:[[cell]]}`, cell là chữ (có thể chứa `{slot}`) | `{accepted}` | `{text}` |
| `IELTS_FLOWCHART` | `{slot, maxWords?}` | `{kind:'FLOW', title?, steps:[text]}`, step chứa `{slot}` | `{accepted}` | `{text}` |
| `IELTS_FORM` | `{slot, maxWords?}` | `{kind:'FORM', title?, rows:[{label, slot?, value?}]}` | `{accepted}` | `{text}` |
| `IELTS_DIAGRAM` | `{slot, description, maxWords?}` | `{kind:'DIAGRAM', title?, assetId, points:[{slot, x, y}]}` (x, y là % 0–100 trên ảnh; ảnh là media của nhóm, role DIAGRAM) | `{accepted}` | `{text}` |
| `IELTS_MAP` | `{label}` (vd "Café") | `{kind:'MAP', title?, assetId, options:[{id:'A', x, y}]}` | `{option}` | `{option}` |
| `IELTS_SHORT` | `{question, maxWords?}` | — | `{accepted}` | `{text}` |

Kiểm layout khi lưu nhóm và khi lưu câu: mọi `slot` của câu phải có trong layout, mỗi slot đúng một câu, `option`/`word` của đáp án phải có trong layout. `reuse:false` chỉ là gợi ý hiển thị (IELTS không chặn chọn trùng), chấm vẫn từng câu.

## Đợt sau

- `LISTEN_RECORD` (10.23): như Nói, nhóm có audio, học viên chỉ tải tệp.

## Video từ nối (10.32, 11.32; D-159)

- Kiểu `VIDEO_CONNECTIVE`: câu quiz trong nhóm có video (D-156). Payload `{multiple?: bool, explanation?}` (giải thích tiếng Việt hiện sau khi chốt), lựa chọn ở `question_choices` như trắc nghiệm (đúng ở `is_correct`); chấm cả câu (chọn đủ và đúng mọi đáp án).
- **Một lượt làm bài = một lượt xem.** "Làm lại từ đầu" = bắt đầu lượt mới; lịch sử lượt cũ giữ như mọi bộ.
- **Xem đủ mới mở quiz:** trình phát chặn tạm dừng và tua tới trong lượt đầu xem; web gửi tiến độ xem định kỳ `POST /api/attempts/{id}/video-progress {groupId, position}`; server chỉ cộng phần đã xem khi `position` tăng không quá thời gian thực trôi qua kể từ lần báo trước (+ dung sai cấu hình) để chống tua; lưu theo (lượt, nhóm). Quiz (GET đáp/kiểm tra/nộp) của nhóm bị chặn 409 `video_not_watched` khi chưa đạt ngưỡng (mặc định 100%, cấu hình).
- **Tối đa 2 lần nộp mỗi lượt** (cấu hình): lần 1 sai thì báo ô sai, chưa lộ đáp án; lần cuối hoặc đúng thì chốt lượt, hiện đáp án đúng + giải thích. Dùng lại cơ chế Kiểm tra/thử lại của D-150 nếu khớp, không thì làm riêng.
- **Lượt đạt** khi mọi câu quiz đúng trong số lần nộp cho phép; **bộ hoàn thành** khi có số lượt đạt >= mặc định 1 (cấu hình). Màn kết quả: "Lượt N · Đã chốt", số lượt đã đạt / cần đạt.
- Chữ giao diện không dùng "AI".

## Video phản xạ (10.28–10.31, 11.28–11.31; D-158)

- Kiểu `VIDEO_SHORT`, `VIDEO_CUE`, `VIDEO_DISCUSS`, `VIDEO_PICTURE`: mỗi câu là một mốc trong video của nhóm. Payload chung `{at (giây), prompt, maxSeconds?}`; `VIDEO_CUE` thêm `cue`; `VIDEO_DISCUSS` thêm `instruction`; `VIDEO_PICTURE` có ảnh là media của câu. `maxSeconds` mặc định 30 (SHORT) / 60 (còn lại), cấu hình được.
- Video tự dừng ở mốc của câu; khi ghi âm video bị khóa; có "Nghe lại đoạn câu hỏi" (phát lại từ mốc trước tới mốc này).
- Nói: chấm qua luồng Nói gắn lượt (D-145, D-147). Tối đa 3 lần ghi mỗi câu mỗi lượt (cấu hình); lấy lần ghi mới nhất (D-145 mục 4).
- `VIDEO_SHORT` có bước Viết trước: câu trả lời viết được chấm tự động (luồng Viết gắn lượt), tối đa 3 lần nộp (cấu hình), đạt ngưỡng (mặc định 70%, cấu hình) mới mở bước Nói; hết lượt viết mà chưa đạt thì vẫn mở bước Nói. Điểm câu = điểm Nói.
- Giao diện: toàn màn hình + xin micro (D-158, không giám sát); trạng thái chấm ghi "Cam đang chấm bài" / "Giáo viên đang chấm bài"; tiêu chí hiển thị theo dạng (Figma), không dùng chữ "AI".
