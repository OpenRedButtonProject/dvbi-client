package org.orbtv.dvbiclient;

import java.util.function.Consumer;

public class ITvInputCallback {
    public int getParentalControlAge() { return 0; }

    /** True while the user has PIN-overridden parental block for the current service. */
    public boolean isParentalAccessOverridden() { return false; }

    /** Clear session PIN override (e.g. when content rating becomes allowed). */
    public void clearParentalAccessOverride() { }

    /**
     * Prompt the terminal unlock UI. {@code result} receives true if approved, false if denied.
     */
    public void requestParentalAccessOverride(Consumer<Boolean> result) {
        if (result != null) {
            result.accept(false);
        }
    }

    /** Kill the running HbbTV / linked application (Annex O kill path). */
    public void destroyHbbtvApplication() { }

    public boolean tuneBroadcast(String uri) { return false; }
    public void tuneOffBroadcast() { }
    public void notifyVideoAvailable() { }
    public void notifyVideoUnavailable(int reason) { }
    public void updateEventPeriods() { }
    public void updateNowNextEvents() { }
}
