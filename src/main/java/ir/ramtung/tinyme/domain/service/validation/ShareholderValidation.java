package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class ShareholderValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();
        if (repositories.getShareholderRepository().findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        return errors;
    }
}
