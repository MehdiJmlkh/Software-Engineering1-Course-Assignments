package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TradePublisher implements Publisher {
    @Override
    public void changeMatchingStateRqHandled(ChangeMatchingStateRq changeMatchingStateRq, List<Trade> trades, EventPublisher eventPublisher) {
        for (Trade trade : trades) {
            eventPublisher.publish(new TradeEvent(changeMatchingStateRq.getSecurityIsin(),
                    trade.getPrice(), trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
        }
    }
}
