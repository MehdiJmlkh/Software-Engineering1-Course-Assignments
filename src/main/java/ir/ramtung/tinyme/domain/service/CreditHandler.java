package ir.ramtung.tinyme.domain.service;


import ir.ramtung.tinyme.domain.entity.CreditOutCome;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Side;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.stereotype.Service;

@Service
public class CreditHandler {
    public CreditOutCome handleTradeCredit(Order newOrder, Trade trade) {
        if (newOrder.getSide() == Side.BUY) {
            if (trade.buyerHasEnoughCredit()) {
                trade.decreaseBuyersCredit();
            } else {
                return CreditOutCome.NOT_ENOUGH;
            }
        }
        trade.increaseSellersCredit();
        return CreditOutCome.ENOUGH;
    }
}
