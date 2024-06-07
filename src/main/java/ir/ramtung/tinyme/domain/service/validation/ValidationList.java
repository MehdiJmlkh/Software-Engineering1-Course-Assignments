package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class ValidationList {
    @Autowired
    private List<Validation> validations;

    public void validate (EnterOrderRq enterOrderRq, Repositories repositories) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        for (Validation validation : validations) {
            errors.addAll(validation.validate(enterOrderRq, repositories));
        }
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    public void validate (DeleteOrderRq deleteOrderRq, Repositories repositories) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        for (Validation validation : validations) {
            errors.addAll(validation.validate(deleteOrderRq, repositories));
        }
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    public void validate (ChangeMatchingStateRq changeMatchingStateRq, Repositories repositories) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        for (Validation validation : validations) {
            errors.addAll(validation.validate(changeMatchingStateRq, repositories));
        }
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

}
