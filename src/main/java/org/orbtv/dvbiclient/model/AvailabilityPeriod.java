package org.orbtv.dvbiclient.model;

import static org.orbtv.dvbiclient.Utils.getSecondsFromDate;
import static org.orbtv.dvbiclient.Utils.getSecondsFromTime;

import android.content.ContentValues;

import org.xmlpull.v1.XmlPullParser;
import java.util.ArrayList;
import java.util.List;

public final class AvailabilityPeriod {
    public static final String DB_COLUMN_VALID_FROM = "valid_from";
    public static final String DB_COLUMN_VALID_TO = "valid_to";
    private List<Interval> mIntervals = new ArrayList<>();
    private Long mValidFrom;
    private Long mValidTo;

    private AvailabilityPeriod() { }

    public List<Interval> getIntervals() { return mIntervals; }
    public Long getValidFrom() { return mValidFrom; }
    public Long getValidTo() { return mValidTo; }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("--- " + this.getClass().getSimpleName() + " ---\nValid From: " + mValidFrom + "\nValid To: " + mValidTo);
        for (Interval interval : mIntervals) {
            builder.append("\n" + interval.toString());
        }
        return builder.toString();
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(AvailabilityPeriod.DB_COLUMN_VALID_FROM, mValidFrom);
        values.put(AvailabilityPeriod.DB_COLUMN_VALID_TO, mValidTo);
        return values;
    }

    public static List<AvailabilityPeriod> parseFromXML(XmlPullParser xpp) throws Exception {
        Builder builder = new Builder();
        ArrayList<Interval> intervals = new ArrayList<>();
        ArrayList<AvailabilityPeriod> periods = new ArrayList<>();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "Availability".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "Period":
                        builder = new Builder();
                        intervals = new ArrayList<>();
                        builder.setValidFrom(getSecondsFromDate(xpp.getAttributeValue(null, "validFrom")))
                                .setValidTo(getSecondsFromDate(xpp.getAttributeValue(null, "validTo")));
                        break;
                    case "Interval":
                        intervals.add(new Interval.Builder()
                                .setStartTime(getSecondsFromTime(xpp.getAttributeValue(null, "startTime")))
                                .setEndTime(getSecondsFromTime(xpp.getAttributeValue(null, "endTime")))
                                .setDays(xpp.getAttributeValue(null, "days"))
                                .build());
                        break;
                }
            }
            else if (eventType == XmlPullParser.END_TAG && "Period".equals(xpp.getName())){
                periods.add(builder.setIntervals(intervals).build());
            }
            eventType = xpp.next();
        }
        return periods;
    }

    public static final class Interval {
        public static final String DB_COLUMN_START_TIME = "start_time";
        public static final String DB_COLUMN_END_TIME = "end_time";
        public static final String DB_COLUMN_DAYS = "days";
        private Integer mStart;
        private Integer mEnd;
        private String mDays;
        private Interval() { }
        public Integer getStartTime() { return mStart; }
        public Integer getEndTime() { return mEnd; }
        public String getDays() { return mDays; }
        @Override
        public String toString() {
            return "- Interval -\nStart Time: " + mStart + ", End time: " + mEnd + ", Days: " + mDays;
        }

        public ContentValues toContentValues() {
            ContentValues values = new ContentValues();
            values.put(AvailabilityPeriod.Interval.DB_COLUMN_START_TIME, mStart);
            values.put(AvailabilityPeriod.Interval.DB_COLUMN_END_TIME, mEnd);
            values.put(AvailabilityPeriod.Interval.DB_COLUMN_DAYS, mDays);
            return values;
        }

        public static final class Builder {
            private Interval mInstance;
            public Builder() {
                mInstance = new Interval();
            }
            public Interval.Builder setStartTime(Integer value) {
                mInstance.mStart = value;
                return this;
            }
            public Interval.Builder setEndTime(Integer value) {
                mInstance.mEnd = value;
                return this;
            }
            public Interval.Builder setDays(String value) {
                mInstance.mDays = value;
                return this;
            }
            public Interval build() {
                Interval instance = new Interval();
                instance.mStart = mInstance.mStart;
                instance.mEnd = mInstance.mEnd;
                instance.mDays = mInstance.mDays;
                return instance;
            }
        }
    }

    public static final class Builder {
        private AvailabilityPeriod mInstance;
        public Builder() {
            mInstance = new AvailabilityPeriod();
        }
        public AvailabilityPeriod.Builder setIntervals(List<Interval> value) {
            mInstance.mIntervals = value;
            return this;
        }
        public AvailabilityPeriod.Builder setValidFrom(Long value) {
            mInstance.mValidFrom = value;
            return this;
        }
        public AvailabilityPeriod.Builder setValidTo(Long value) {
            mInstance.mValidTo = value;
            return this;
        }
        public AvailabilityPeriod build() {
            AvailabilityPeriod instance = new AvailabilityPeriod();
            instance.mIntervals = mInstance.mIntervals;
            instance.mValidFrom = mInstance.mValidFrom;
            instance.mValidTo = mInstance.mValidTo;
            return instance;
        }
    }
}
