package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.IcebergOrder;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class PeakSizeValidation implements Validation {
    @Override
    public List<String> validate(EnterOrderRq enterOrderRq, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);

        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security != null) {
            Order order = security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if ((order instanceof IcebergOrder) && enterOrderRq.getPeakSize() == 0)
                errors.add(Message.INVALID_PEAK_SIZE);
            if (order != null && !(order instanceof IcebergOrder) && enterOrderRq.getPeakSize() != 0)
                errors.add(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        }
        return errors;
    }
}
