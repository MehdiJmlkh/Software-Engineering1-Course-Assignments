package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class StopPriceValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getStopPrice() > 0) {
            if (enterOrderRq.getMinimumExecutionQuantity() > 0)
                errors.add(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_FOR_A_STOP_LIMIT_ORDER);
            if (enterOrderRq.getPeakSize() > 0)
                errors.add(Message.STOP_LIMIT_ORDER_CAN_NOT_BE_ICEBERG_ORDER);
        }

        Security security = repositories.getSecurityRepository().findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security != null) {
            if (security.getMatchingState() == MatchingState.AUCTION && enterOrderRq.getStopPrice() != 0)
                errors.add(Message.CANNOT_SUBMIT_OR_UPDATE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE);

            Order order = security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if (order != null && !(order instanceof StopLimitOrder) && enterOrderRq.getStopPrice() > 0)
                    errors.add(Message.CANNOT_SPECIFY_STOP_PRICE_FOR_A_ACTIVATED_ORDER);
        }

        return errors;
    }

    @Override
    public List<String> validate(DeleteOrderRq deleteOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();

        Security security = repositories.getSecurityRepository().findSecurityByIsin(deleteOrderRq.getSecurityIsin());
        if (security != null && security.getMatchingState() == MatchingState.AUCTION)
                errors.add(Message.CANNOT_DELETE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE);

        return errors;
    }
}
