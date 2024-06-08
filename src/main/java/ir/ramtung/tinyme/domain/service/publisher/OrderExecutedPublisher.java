package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class OrderExecutedPublisher implements Publisher {
    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }

    @Override
    public void activatedOrderExecuted(StopLimitOrder order, MatchResult matchResult, EventPublisher eventPublisher) {
        if (!matchResult.trades().isEmpty())
            eventPublisher.publish(new OrderExecutedEvent(order.getRequestId(), order.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
    }
}
