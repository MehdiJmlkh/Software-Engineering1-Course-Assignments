package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public interface Publisher {
    default void invalidRequestExceptionOccured(long requestId, long orderId, InvalidRequestException ex, EventPublisher eventPublisher) {}
    default void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {}
    default void deleteOrderRqHandled(DeleteOrderRq deleteOrderRq, Security security, EventPublisher eventPublisher) {}
    default void changeMatchingStateRqHandled(ChangeMatchingStateRq changeMatchingStateRq, List<Trade> trades, EventPublisher eventPublisher) {}
    default void queuedOrderActivated(StopLimitOrder order, EventPublisher eventPublisher) {}
    default void activatedOrderExecuted(StopLimitOrder order, MatchResult matchResult,EventPublisher eventPublisher) {}
}
