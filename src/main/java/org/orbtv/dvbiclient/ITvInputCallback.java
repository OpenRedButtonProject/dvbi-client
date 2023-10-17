package org.orbtv.dvbiclient;

import android.content.Context;

public interface ITvInputCallback {
    Context getContext();
    int getParentalControlAge();
}
