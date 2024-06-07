package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.Repositories;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class OrderValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);

        Security security = repositories.getSecurityRepository().findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security != null && enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER) {
            Order order = security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if (order == null)
                errors.add(Message.ORDER_ID_NOT_FOUND);
        }
        return errors;
    }

    @Override
    public List<String> validate(DeleteOrderRq deleteOrderRq, Repositories repositories) {
        List<String> errors = new LinkedList<>();

        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);

        Security security = repositories.getSecurityRepository().findSecurityByIsin(deleteOrderRq.getSecurityIsin());
        if (security != null) {
            Order order = security.getOrderBook().findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            if (order == null)
                errors.add(Message.ORDER_ID_NOT_FOUND);
        }
        return errors;
    }
}
