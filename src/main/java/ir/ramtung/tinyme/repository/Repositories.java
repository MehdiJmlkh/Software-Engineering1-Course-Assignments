package ir.ramtung.tinyme.repository;

import lombok.Getter;

@Getter
public class Repositories {
    private final ShareholderRepository shareholderRepository;
    private final SecurityRepository securityRepository;
    private final BrokerRepository brokerRepository;

    public Repositories(ShareholderRepository shareholderRepository,
                        SecurityRepository securityRepository,
                        BrokerRepository brokerRepository) {
        this.shareholderRepository = shareholderRepository;
        this.securityRepository = securityRepository;
        this.brokerRepository =brokerRepository;
    }
}
