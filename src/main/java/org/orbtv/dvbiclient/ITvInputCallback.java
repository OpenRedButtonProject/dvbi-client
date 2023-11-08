package org.orbtv.dvbiclient;

import android.content.Context;

public class ITvInputCallback {
    protected Context getContext() { return null; }
    protected int getParentalControlAge() { return 0; }
    protected void tuneBroadcast(String uri) { }
    protected void tuneOffBroadcast() { }
    protected void notifyVideoAvailable() { }
    protected void notifyVideoUnavailable(int reason) { }
}
