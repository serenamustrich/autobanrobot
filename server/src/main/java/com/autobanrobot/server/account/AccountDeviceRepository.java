package com.autobanrobot.server.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountDeviceRepository extends JpaRepository<AccountDevice, Long> {
    Optional<AccountDevice> findByInstallationId(String installationId);
}
