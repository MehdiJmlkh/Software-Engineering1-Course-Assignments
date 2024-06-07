package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class BrokerValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();
        if (repositories.getBrokerRepository().findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        return errors;
    }
}
