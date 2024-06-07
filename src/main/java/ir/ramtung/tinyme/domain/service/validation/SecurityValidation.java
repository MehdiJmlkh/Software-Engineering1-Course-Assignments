package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class SecurityValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        List<String> errors = new LinkedList<>();

        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        return errors;
    }

    @Override
    public List<String> validate(DeleteOrderRq deleteOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        List<String> errors = new LinkedList<>();
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        return errors;
    }

    @Override
    public List<String> validate(ChangeMatchingStateRq changeMatchingStateRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        List<String> errors = new LinkedList<>();
        if (securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        return errors;
    }
}
