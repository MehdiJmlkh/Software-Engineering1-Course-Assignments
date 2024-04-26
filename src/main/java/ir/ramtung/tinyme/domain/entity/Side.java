package ir.ramtung.tinyme.domain.entity;

public enum Side {
    BUY {
        @Override
        public Side opposite() {
            return SELL;
        }
        public Side stop() {
            return STOP_BUY;
        }
    },
    SELL {
        @Override
        public Side opposite() {
            return BUY;
        }
        public Side stop() {
            return STOP_SELL;
        }
    },
    STOP_BUY {
        @Override
        public Side opposite() {
            return STOP_SELL;
        }
        public Side stop() {
            return this;
        }
    },
    STOP_SELL {
        @Override
        public Side opposite() {
            return STOP_BUY;
        }
        public Side stop() {
            return this;
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
    public abstract Side stop();
}
