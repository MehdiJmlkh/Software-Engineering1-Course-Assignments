package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class MinimumExecutionQuantityValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity())
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_NOT_LESS_THAN_OR_EQUAL_TO_QUANTITY);

        Security security = repositories.getSecurityRepository().findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security != null) {
            if (security.getMatchingState() == MatchingState.AUCTION && enterOrderRq.getMinimumExecutionQuantity() > 0)
                errors.add(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_IN_THE_AUCTION_STATE);

            Order order = security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if (order != null && order.getMinimumExecutionQuantity() != enterOrderRq.getMinimumExecutionQuantity())
                errors.add(Message.MINIMUM_EXECUTION_QUANTITY_OF_UPDATE_ORDER_HAS_CHANGED);
        }

        return errors;
    }
}
