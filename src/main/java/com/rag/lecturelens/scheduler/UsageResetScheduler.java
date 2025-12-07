package com.rag.lecturelens.scheduler;

import com.rag.lecturelens.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsageResetScheduler {

    private final AppUserRepository appUserRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetDailyUsage() {
        appUserRepository.resetUsageLimit();
    }
}
