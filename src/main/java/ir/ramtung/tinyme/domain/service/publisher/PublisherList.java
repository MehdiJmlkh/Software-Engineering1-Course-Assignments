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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublisherList {
    @Autowired
    private List<Publisher> publishers;

    public void invalidRequestExceptionOccured(long requestId, long orderId, InvalidRequestException ex, EventPublisher eventPublisher) {
        for(Publisher publisher : publishers)
            publisher.invalidRequestExceptionOccured(requestId, orderId, ex, eventPublisher);
    }

    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        for (Publisher publisher : publishers)
            publisher.enterOrderRqHandled(enterOrderRq, matchResult, security, eventPublisher);
    }

    public void changeMatchingStateRqHandled(ChangeMatchingStateRq changeMatchingStateRq, List<Trade> trades, EventPublisher eventPublisher) {
        for (Publisher publisher : publishers)
            publisher.changeMatchingStateRqHandled(changeMatchingStateRq, trades, eventPublisher);
    }

    public void queuedOrderActivated(StopLimitOrder order, EventPublisher eventPublisher) {
        for (Publisher publisher : publishers)
            publisher.queuedOrderActivated(order, eventPublisher);
    }

    public void deleteOrderRqHandled(DeleteOrderRq deleteOrderRq, Security security, EventPublisher eventPublisher) {
        for (Publisher publisher : publishers)
            publisher.deleteOrderRqHandled(deleteOrderRq, security, eventPublisher);
    }

    public void activatedOrderExecuted(StopLimitOrder order, MatchResult matchResult,EventPublisher eventPublisher) {
        for (Publisher publisher : publishers)
            publisher.activatedOrderExecuted(order, matchResult, eventPublisher);
    }
}
