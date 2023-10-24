package org.orbtv.dvbiclient;

import android.content.Context;

public interface ITvInputCallback {
    Context getContext();
    int getParentalControlAge();
    void tune(String uri);
    void tuneOff();
}
