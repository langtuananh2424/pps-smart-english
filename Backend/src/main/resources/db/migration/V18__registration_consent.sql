-- FR-AUTH-01, D-114, D-129: ngày sinh và chấp thuận điều khoản khi đăng ký. Null với tài khoản tạo trước đó.
ALTER TABLE users ADD COLUMN date_of_birth date;
ALTER TABLE users ADD COLUMN terms_version varchar(20);
ALTER TABLE users ADD COLUMN terms_accepted_at timestamp with time zone;
