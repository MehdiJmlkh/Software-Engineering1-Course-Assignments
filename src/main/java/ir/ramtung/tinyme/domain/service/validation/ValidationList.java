package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class ValidationList {
    @Autowired
    private List<Validation> validations;

    public List<String> validate(EnterOrderRq enterOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        List<String> errors = new LinkedList<>();
        for (Validation validation : validations) {
            errors.addAll(validation.validate(enterOrderRq, securityRepository, brokerRepository, shareholderRepository));
        }
        return errors;
    }

}
