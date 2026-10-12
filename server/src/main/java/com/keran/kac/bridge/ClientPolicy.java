package com.keran.kac.bridge;

/** Client registration alone never grants admission. */
public final class ClientPolicy {
    private ClientPolicy() {}
    public static boolean canPlay(boolean strict, boolean bypass, boolean validReport) {
        return !strict || bypass || validReport;
    }
}
