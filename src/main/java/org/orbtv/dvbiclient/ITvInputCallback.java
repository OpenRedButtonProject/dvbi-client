package org.orbtv.dvbiclient;

public interface ITvInputCallback {
    int getParentalControlAge();
    void tuneBroadcast(String uri);
    void tuneOffBroadcast();
    void notifyVideoAvailable();
    void notifyVideoUnavailable(int reason);
}
