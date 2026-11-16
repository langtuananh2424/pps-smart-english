package com.pps.smartenglish.home.service.cam;

/** Toàn bộ câu chữ của Cam ở một chỗ để dễ sửa giọng văn. */
final class CamTexts {

    private CamTexts() {}

    static String greeting(int hour, String name) {
        String part = hour < 11 ? "buổi sáng" : hour < 14 ? "buổi trưa" : hour < 18 ? "buổi chiều" : "buổi tối";
        return "Chào " + part + ", " + name + "! Cam sẵn sàng học cùng bạn nhé.";
    }

    static String openAttempt(String bankTitle, Long remainingSec) {
        String base = "Bạn đang làm dở \"" + bankTitle + "\"";
        if (remainingSec == null) {
            return base + ", vào làm tiếp nhé!";
        }
        return base + ", còn khoảng " + Math.max(1, (remainingSec + 59) / 60) + " phút. Làm tiếp nhé!";
    }

    static String keepStreak(int streak) {
        return "Hôm nay bạn chưa học. Học một chút để giữ chuỗi " + streak + " ngày nào!";
    }

    static String startStreak() {
        return "Hôm nay bạn chưa học. Bắt đầu một bài ngắn để mở chuỗi ngày học mới nhé!";
    }

    static String courseExpiring(String title, long daysLeft) {
        return "Khóa \"" + title + "\" còn " + daysLeft + " ngày. Cùng tranh thủ học nhé!";
    }

    static String unread(long count) {
        return "Bạn có " + count + " thông báo mới.";
    }

    static final String CONTINUE = "Làm tiếp";
    static final String VIEW_COURSE = "Xem khóa học";
}
