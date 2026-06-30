package org.orbtv.dvbiclient;

public class ITvInputCallback {
    public int getParentalControlAge() { return 0; }
    public boolean tuneBroadcast(String uri) { return false; }
    public void tuneOffBroadcast() { }
    public void notifyVideoAvailable() { }
    public void notifyVideoUnavailable(int reason) { }
    public void updateEventPeriods() { }
    public void updateNowNextEvents() { }
}
