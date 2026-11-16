package com.pps.smartenglish.home.service.cam;

import com.pps.smartenglish.home.dto.HomeDtos.CamMessage;
import java.util.Optional;

/**
 * Strategy cho một loại lời nhắn của Cam (D-53). Thứ tự ưu tiên là @Order của bean: số nhỏ đứng trước.
 * Thêm loại mới = thêm một bean, không sửa CamService.
 */
public interface CamRule {

    Optional<CamMessage> evaluate(CamContext ctx);
}
