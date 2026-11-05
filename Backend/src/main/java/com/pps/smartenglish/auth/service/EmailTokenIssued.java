package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.EmailToken.Purpose;

/** Token email vừa được tạo; AuthMailListener gửi thư sau khi transaction (nếu có) commit. */
public record EmailTokenIssued(String to, Purpose purpose, String token) {}
