package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.BankInfo;

/** Strategy quyền làm bộ câu hỏi: các bean được OR với nhau, chỉ cần một bean cho phép. */
public interface BankAccessPolicy {

    boolean allows(Long userId, BankInfo bank);
}
