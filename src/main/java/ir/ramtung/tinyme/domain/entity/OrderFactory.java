package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import lombok.Builder;


@Builder
public class OrderFactory {
    private SecurityRepository securityRepository;
    private BrokerRepository brokerRepository;
    private ShareholderRepository shareholderRepository;
    public Order createOrder(EnterOrderRq enterOrderRq){
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
        Order order;

        if (enterOrderRq.getPeakSize() == 0)
            if(enterOrderRq.getStopPrice() == 0)
                order = new Order(enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
            else
                order = new StopLimitOrder(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getStopPrice());
        else
            order = new IcebergOrder(enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        return order;
    }
}
