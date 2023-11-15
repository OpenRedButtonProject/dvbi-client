package org.orbtv.dvbiclient.model;

public class Programme {
    private int mParentalRating;
    private String mTitle;
    private String mShortDescription;
    private String mMediumDescription;
    private String mLongDescription;
    private long mStartTime;
    private long mEndTime;

    private Programme() { }

    public int getParentalRating() { return mParentalRating; }
    public String getTitle() { return mTitle; }
    public String getShortDescription() { return mShortDescription; }
    public String getMediumDescription() { return mMediumDescription; }
    public String getLongDescription() { return mLongDescription; }
    public long getStartTime() { return mStartTime; }
    public long getEndTime() { return mEndTime; }

    public static class Builder {
        private Programme mInstance;

        public Builder() {
            mInstance = new Programme();
        }

        public Programme.Builder setParentalRating(int value) {
            mInstance.mParentalRating = value;
            return this;
        }

        public Programme.Builder setTitle(String value) {
            mInstance.mTitle = value;
            return this;
        }

        public Programme.Builder setShortDescription(String value) {
            mInstance.mShortDescription = value;
            return this;
        }

        public Programme.Builder setMediumDescription(String value) {
            mInstance.mMediumDescription = value;
            return this;
        }

        public Programme.Builder setLongDescription(String value) {
            mInstance.mLongDescription = value;
            return this;
        }

        public Programme.Builder setStartTime(long value) {
            mInstance.mStartTime = value;
            return this;
        }

        public Programme.Builder setEndTime(long value) {
            mInstance.mEndTime = value;
            return this;
        }

        public Programme build() {
            Programme instance = new Programme();
            instance.mParentalRating = mInstance.mParentalRating;
            instance.mTitle = mInstance.mTitle;
            instance.mShortDescription = mInstance.mShortDescription;
            instance.mMediumDescription = mInstance.mMediumDescription;
            instance.mLongDescription = mInstance.mLongDescription;
            instance.mStartTime = mInstance.mStartTime;
            instance.mEndTime = mInstance.mEndTime;
            return instance;
        }
    }
}
