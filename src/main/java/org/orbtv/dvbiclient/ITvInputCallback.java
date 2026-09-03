/**
 * ORB Software. Copyright (c) 2026 Ocean Blue Software Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
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
