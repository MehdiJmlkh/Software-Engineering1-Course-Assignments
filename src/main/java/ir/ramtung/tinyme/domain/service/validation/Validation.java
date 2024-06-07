package ir.ramtung.tinyme.domain.service.validation;


import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;

import java.util.LinkedList;
import java.util.List;

public interface Validation {
    default List<String> validate(EnterOrderRq enterOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) { return new LinkedList<>(); }
    default List<String> validate(DeleteOrderRq deleteOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) { return new LinkedList<>(); }
    default List<String> validate(ChangeMatchingStateRq changeMatchingStateRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) { return new LinkedList<>(); }
}