package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderRejectedPublisher implements Publisher {
    @Override
    public void invalidRequestExceptionOccured(long requestId, long orderId, InvalidRequestException ex,EventPublisher eventPublisher) {
        eventPublisher.publish(new OrderRejectedEvent(requestId, orderId, ex.getReasons()));
    }

    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        switch (matchResult.outcome()) {
            case NOT_ENOUGH_CREDIT:
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                break;
            case NOT_ENOUGH_POSITIONS:
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                break;
            case MINIMUM_QUANTITY_NOT_SATISFIED:
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.ORDER_HAS_NOT_EXECUTED_MINIMUM_EXECUTION_QUANTITY)));
                break;
            case NOT_EQUAL_MINIMUM_EXECUTION_QUANTITY:
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.MINIMUM_EXECUTION_QUANTITY_OF_UPDATE_ORDER_HAS_CHANGED)));
                break;
        }
    }
}
