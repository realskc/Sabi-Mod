package top.sabi;

public final class SabiClientState {
    private static long balance;

    private SabiClientState() {
    }

    public static long balance() {
        return balance;
    }

    public static void setBalance(long balance) {
        SabiClientState.balance = Math.max(0, balance);
    }
}
