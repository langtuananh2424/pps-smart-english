# Dùng pps-speaking-engine khi dev ở local

Engine chạy trên server `pps-education-01` (container `speaking-engine`). Nó chỉ nghe ở `127.0.0.1:8100` nên không gọi thẳng được từ laptop: phải đi qua SSH tunnel.

## 1. Lấy token

Mỗi ứng dụng có token riêng. Token khai báo ở `/home/ppsadmin/pps-speaking-engine/.env`, biến `ENGINE_TOKENS`, dạng `ten:token,ten:token`. Token dev của app này tên là `smart-english-dev`.

```bash
ssh ppsadmin@100.82.186.5 "grep -o 'smart-english-dev:[^,]*' ~/pps-speaking-engine/.env | cut -d: -f2"
```

Không có kết quả nghĩa là token chưa được tạo. Xem mục [Thêm token mới](#thêm-token-mới). Không commit token vào git.

## 2. Mở tunnel (mỗi lần dev)

Mở một cửa sổ terminal riêng và để nguyên:

```bash
ssh -N -L 8100:127.0.0.1:8100 ppsadmin@100.82.186.5
```

Laptop phải ở trong tailnet. Nếu đang ở mạng LAN văn phòng thì thay IP bằng `192.168.100.90`.

## 3. Khai báo trong `.env` ở gốc repo

```
ENGINE_URL=http://localhost:8100
ENGINE_TOKEN=<token smart-english-dev>
```

Chọn `ENGINE_URL` theo cách chạy Backend:

| Backend chạy ở đâu | `ENGINE_URL` |
|---|---|
| Trực tiếp trên laptop (IDE, `mvn spring-boot:run`) | `http://localhost:8100` |
| Trong `docker compose` trên laptop | `http://host.docker.internal:8100` |

Bên trong container, `localhost` là chính container đó chứ không phải laptop.

## 4. Kiểm tra

```bash
curl.exe -X POST http://localhost:8100/v1/transcribe -H "Authorization: Bearer <TOKEN>" -F "audio=@sample.wav" -F "language=en"
```

Nhận được JSON có `text` là đã thông. Gặp `401` là sai token. Gặp `Connection refused` là tunnel chưa mở.

## API của engine

Mọi request đều dùng `POST` multipart/form-data, header `Authorization: Bearer <token>`, và field `audio` là file WAV. Request có thể chạy lâu, nên đặt read timeout khoảng 180s.

| Endpoint | Field | Trả về |
|---|---|---|
| `/v1/transcribe` | `language` (vd `en`) | `text`, `words[]` gồm `word`, `start`, `end`, `probability` |
| `/v1/analyze` | `words` (mảng JSON các từ mong đợi), `gopt_model`, `ipa_model` (để rỗng thì dùng mặc định), `return_features` (`0`/`1`) | `ipa.words[]` gồm `expected_ipa`, `score`, `issues`, `heard_ipa`; `gopt` gồm `utterance`, `words`, `phones` |

Client tham khảo là `app/engine_client.py` trong repo `pps-speaking-lab`.

## Thêm token mới

Chạy trên server, trong `~/pps-speaking-engine`:

```bash
cp .env .env.bak
NEW=$(openssl rand -hex 20) && sed -i "s/^ENGINE_TOKENS=.*/&,smart-english-dev:$NEW/" .env && echo "$NEW"
docker compose up -d
```

`up -d` sẽ tạo lại container. Engine phải nạp lại model nên ngừng chạy từ vài chục giây đến vài phút, và pps-education (staging lẫn production) cũng bị ảnh hưởng. Vì vậy chỉ chạy vào giờ vắng. Nếu có lỗi, khôi phục bằng `cp .env.bak .env && docker compose up -d`.
