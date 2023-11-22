package org.orbtv.dvbiclient.model;

import android.content.ContentValues;

public class Programme {
    public static final String DB_COLUMN_TITLE = "title";
    public static final String DB_COLUMN_PROGRAM_ID = "program_id";
    public static final String DB_COLUMN_SHORT_DESCRIPTION = "short_description";
    public static final String DB_COLUMN_MEDIUM_DESCRIPTION = "medium_description";
    public static final String DB_COLUMN_LONG_DESCRIPTION = "long_description";
    public static final String DB_COLUMN_START_TIME = "start_time";
    public static final String DB_COLUMN_END_TIME = "end_time";
    public static final String DB_COLUMN_MINIMUM_AGE = "minimum_age";
    public static final String DB_COLUMN_PARENTAL_RATING = "parental_rating";
    public static final String DB_COLUMN_PARENTAL_RATING_DESC = "parental_rating_desc";
    private String mParentalRating;
    private String mParentalRatingDesc;
    private int mMinimumAge;
    private String mProgramId;
    private String mTitle;
    private String mShortDescription;
    private String mMediumDescription;
    private String mLongDescription;
    private long mStartTime;
    private long mEndTime;

    private Programme() { }

    public String getParentalRatingScheme() { return mParentalRating; }
    public String getParentalRatingDescription() { return mParentalRatingDesc; }
    public String getTitle() { return mTitle; }
    public String getShortDescription() { return mShortDescription; }
    public String getMediumDescription() { return mMediumDescription; }
    public String getLongDescription() { return mLongDescription; }
    public long getStartTime() { return mStartTime; }
    public long getEndTime() { return mEndTime; }
    public String getProgramId() { return mProgramId; }
    public int getMinimumAge() { return mMinimumAge; }
    @Override
    public String toString() {
        return "Programme {"
                + " mTitle: " + mTitle
                + ", mProgramId: " + mProgramId
                + ", mShortDescription: " + mShortDescription
                + ", mMediumDescription: " + mMediumDescription
                + ", mLongDescription: " + mLongDescription
                + ", mStartTime: " + mStartTime
                + ", mEndTime: " + mEndTime
                + ", mMinimumAge: " + mMinimumAge
                + ", mParentalRating: " + mParentalRating
                + ", mParentalRatingDesc: " + mParentalRatingDesc
                + " }";
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(Programme.DB_COLUMN_TITLE, mTitle);
        values.put(Programme.DB_COLUMN_PROGRAM_ID, mProgramId);
        values.put(Programme.DB_COLUMN_SHORT_DESCRIPTION, mShortDescription);
        values.put(Programme.DB_COLUMN_MEDIUM_DESCRIPTION, mMediumDescription);
        values.put(Programme.DB_COLUMN_LONG_DESCRIPTION, mLongDescription);
        values.put(Programme.DB_COLUMN_PARENTAL_RATING, mParentalRating);
        values.put(Programme.DB_COLUMN_PARENTAL_RATING_DESC, mParentalRatingDesc);
        values.put(Programme.DB_COLUMN_MINIMUM_AGE, mMinimumAge);
        values.put(Programme.DB_COLUMN_START_TIME, mStartTime);
        values.put(Programme.DB_COLUMN_END_TIME, mEndTime);
        return values;
    }

    public static class Builder {
        private Programme mInstance;

        public Builder() {
            mInstance = new Programme();
        }

        public Programme.Builder setParentalRatingScheme(String value) {
            mInstance.mParentalRating = value;
            return this;
        }

        public Programme.Builder setParentalRatingDescription(String value) {
            mInstance.mParentalRatingDesc = value;
            return this;
        }

        public Programme.Builder setMinimumAge(int value) {
            mInstance.mMinimumAge = value;
            return this;
        }

        public Programme.Builder setTitle(String value) {
            mInstance.mTitle = value;
            return this;
        }

        public Programme.Builder setProgramId(String value) {
            mInstance.mProgramId = value;
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
            instance.mParentalRatingDesc = mInstance.mParentalRatingDesc;
            instance.mMinimumAge = mInstance.mMinimumAge;
            instance.mTitle = mInstance.mTitle;
            instance.mProgramId = mInstance.mProgramId;
            instance.mShortDescription = mInstance.mShortDescription;
            instance.mMediumDescription = mInstance.mMediumDescription;
            instance.mLongDescription = mInstance.mLongDescription;
            instance.mStartTime = mInstance.mStartTime;
            instance.mEndTime = mInstance.mEndTime;
            return instance;
        }
    }
}
