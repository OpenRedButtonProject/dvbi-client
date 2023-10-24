package org.orbtv.dvbiclient.model;

import org.xmlpull.v1.XmlPullParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class InstanceAvailabilityPeriod {
    private List<String> mStartTimes = new ArrayList<>();
    private List<String> mEndTimes = new ArrayList<>();

    private InstanceAvailabilityPeriod() { }

    public List<String> getStartTimes() { return new ArrayList<>(mStartTimes); }

    public List<String> getEndTimes() {
        return new ArrayList<>(mEndTimes);
    }

    public long getDuration(String startTime, String endTime) {
        if (startTime == null || endTime == null || startTime.isEmpty() || endTime.isEmpty()) {
            return 0;
        }
        try {
            // check spec for supported formats
            //SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date startDate = format.parse(startTime);
            Date endDate = format.parse(endTime);
            long durationInMillis = endDate.getTime() - startDate.getTime();

            return TimeUnit.MILLISECONDS.toMinutes(durationInMillis);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("- ").append(this.getClass().getSimpleName()).append(" -\n");
        for (int i = 0; i < mStartTimes.size(); i++) {
            builder.append("Start Time: ").append(mStartTimes.get(i)).append("\n");
            builder.append("End Time: ").append(mEndTimes.get(i)).append("\n");
            builder.append("Duration (minutes): ").append(getDuration(mStartTimes.get(i), mEndTimes.get(i))).append("\n");
        }
        return builder.toString();
    }

    public static InstanceAvailabilityPeriod parseFromXML(XmlPullParser xpp) throws Exception {
        ArrayList<String> startTimes = new ArrayList<>();
        ArrayList<String> endTimes = new ArrayList<>();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "Period".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "Interval":
                        startTimes.add(xpp.getAttributeValue(null, "startTime"));
                        endTimes.add(xpp.getAttributeValue(null, "endTime"));
                        break;
                }
            }
            eventType = xpp.next();
        }
        return new Builder()
                .setStartTimes(startTimes)
                .setEndTimes(endTimes)
                .build();
    }

    public static class Builder {
        private InstanceAvailabilityPeriod mInstance;
        public Builder() {
            mInstance = new InstanceAvailabilityPeriod();
        }
        public InstanceAvailabilityPeriod.Builder setStartTimes(List<String> value) {
            mInstance.mStartTimes = value;
            return this;
        }
        public InstanceAvailabilityPeriod.Builder setEndTimes(List<String> value) {
            mInstance.mEndTimes = value;
            return this;
        }
        public InstanceAvailabilityPeriod build() {
            InstanceAvailabilityPeriod instance = new InstanceAvailabilityPeriod();
            instance.mStartTimes = mInstance.mStartTimes;
            instance.mEndTimes = mInstance.mEndTimes;
            return instance;
        }
    }
}
