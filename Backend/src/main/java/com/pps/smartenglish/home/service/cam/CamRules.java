package com.pps.smartenglish.home.service.cam;

import com.pps.smartenglish.home.dto.HomeDtos.CamAction;
import com.pps.smartenglish.home.dto.HomeDtos.CamKind;
import com.pps.smartenglish.home.dto.HomeDtos.CamMessage;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Các quy tắc lời nhắn của Cam, theo thứ tự ưu tiên của D-53 (chào luôn đầu tiên). STREAK và UNREAD không có action vì web chưa có route đích. */
public final class CamRules {

    private CamRules() {}

    @Component
    @Order(0)
    static class Greeting implements CamRule {
        @Override
        public Optional<CamMessage> evaluate(CamContext ctx) {
            return Optional.of(new CamMessage(CamKind.GREETING, CamTexts.greeting(ctx.now().getHour(), ctx.displayName()), null));
        }
    }

    @Component
    @Order(10)
    static class InProgressAttempt implements CamRule {
        @Override
        public Optional<CamMessage> evaluate(CamContext ctx) {
            return ctx.openAttempt().map(a -> new CamMessage(CamKind.IN_PROGRESS_ATTEMPT,
                    CamTexts.openAttempt(a.bankTitle(), a.remainingSec()),
                    new CamAction(CamTexts.CONTINUE, a.path())));
        }
    }

    @Component
    @Order(20)
    static class Streak implements CamRule {
        @Override
        public Optional<CamMessage> evaluate(CamContext ctx) {
            if (ctx.stats().activeToday()) {
                return Optional.empty();
            }
            int streak = ctx.stats().streakCurrent();
            return Optional.of(new CamMessage(CamKind.STREAK,
                    streak > 0 ? CamTexts.keepStreak(streak) : CamTexts.startStreak(), null));
        }
    }

    @Component
    @Order(30)
    static class CourseExpiring implements CamRule {
        @Override
        public Optional<CamMessage> evaluate(CamContext ctx) {
            return ctx.expiring().map(c -> new CamMessage(CamKind.COURSE_EXPIRING,
                    CamTexts.courseExpiring(c.title(), c.daysLeft()),
                    new CamAction(CamTexts.VIEW_COURSE, "/khoa-hoc/" + c.slug())));
        }
    }

    @Component
    @Order(40)
    static class Unread implements CamRule {
        @Override
        public Optional<CamMessage> evaluate(CamContext ctx) {
            return ctx.unread() > 0
                    ? Optional.of(new CamMessage(CamKind.UNREAD, CamTexts.unread(ctx.unread()), null))
                    : Optional.empty();
        }
    }
}
