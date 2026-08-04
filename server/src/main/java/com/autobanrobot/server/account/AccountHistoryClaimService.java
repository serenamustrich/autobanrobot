package com.autobanrobot.server.account;

import com.autobanrobot.server.ban.BanEvent;
import com.autobanrobot.server.ban.BanEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountHistoryClaimService {
    private final BanEventRepository banEvents;
    private final AccountContributionRepository contributions;
    public AccountHistoryClaimService(BanEventRepository banEvents, AccountContributionRepository contributions) { this.banEvents = banEvents; this.contributions = contributions; }
    @Transactional
    public void claim(Long accountId, String installationId) {
        for (BanEvent event : banEvents.findByInstallationIdAndAccountIdIsNull(installationId)) {
            event.assignAccount(accountId);
            contributions.insertIgnore(accountId, event.getUsername(), event.getId(), java.time.Instant.now());
        }
    }
}
