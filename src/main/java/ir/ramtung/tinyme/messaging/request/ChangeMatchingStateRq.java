package ir.ramtung.tinyme.messaging.request;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChangeMatchingStateRq {
    private String securityIsin;
    private MatchingState targetState;

}
