package ir.ramtung.tinyme.domain.entity;

public enum Side {
    BUY {
        @Override
        public Side opposite() {
            return SELL;
        }
    },
    SELL {
        @Override
        public Side opposite() {
            return BUY;
        }
    },
    STOP_BUY {
        @Override
        public Side opposite() {
            return STOP_SELL;
        }
    },
    STOP_SELL {
        @Override
        public Side opposite() {
            return STOP_BUY;
        }
    };

    public static Side parse(String s) {
        if (s.equals("BUY"))
            return BUY;
        else if (s.equals("SELL"))
            return SELL;
        else
            throw new IllegalArgumentException("Invalid value for order side");
    }

    public abstract Side opposite();
}
