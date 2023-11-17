package org.orbtv.dvbiclient.model;

import android.content.ContentValues;

import org.xmlpull.v1.XmlPullParser;

public class RelatedMaterial {
    public static final String DB_COLUMN_HOW_RELATED_HREF = "how_related_href";
    public static final String DB_COLUMN_MEDIA_LOCATOR_URI = "media_locator_uri";
    public static final String DB_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE = "media_locator_content_type";
    private String mHowRelatedHref;
    private String mMediaLocatorUri;
    private String mMediaLocatorContentType;

    private RelatedMaterial() { }

    public String getHowRelatedHref() {
        return mHowRelatedHref;
    }

    public String getMediaLocatorUri() { return mMediaLocatorUri; }

    public String getMediaLocatorContentType() {
        return mMediaLocatorContentType;
    }

    public boolean isXmlAitContentType() {
        return "application/vnd.dvb.ait+xml".equals(mMediaLocatorContentType);
    }

    @Override
    public String toString() {
        return "- " + this.getClass().getSimpleName() + " -\n"
                + "howRelatedHref: " + this.mHowRelatedHref + "\n"
                + "mediaLocatorContentType: " + this.mMediaLocatorContentType + "\n"
                + "mediaLocatorUri: " + this.mMediaLocatorUri;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(RelatedMaterial.DB_COLUMN_HOW_RELATED_HREF, mHowRelatedHref);
        values.put(RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_URI, mMediaLocatorUri);
        values.put(RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE, mMediaLocatorContentType);
        return values;
    }

    public static RelatedMaterial parseFromXML(XmlPullParser xpp) throws Exception {
        RelatedMaterial relatedMaterial = new RelatedMaterial();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "RelatedMaterial".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "HowRelated":
                        relatedMaterial.mHowRelatedHref = xpp.getAttributeValue(null, "href");
                        break;
                    case "MediaLocator":
                        parseMediaLocator(xpp, relatedMaterial);
                        break;
                    case "MediaUri":
                        relatedMaterial.mMediaLocatorContentType = xpp.getAttributeValue(null, "contentType");
                        break;
                }
            }
            eventType = xpp.next();
        }
        return relatedMaterial;
    }

    private static void parseMediaLocator(XmlPullParser xpp, RelatedMaterial relatedMaterial) throws Exception {
        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && "MediaLocator".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "MediaUri".equals(xpp.getName())) {
                relatedMaterial.mMediaLocatorContentType = xpp.getAttributeValue(null, "contentType");
                relatedMaterial.mMediaLocatorUri = xpp.nextText();
            }
            eventType = xpp.next();
        }
    }

    public static class Builder {
        private RelatedMaterial mInstance;
        public Builder() {
            mInstance = new RelatedMaterial();
        }
        public RelatedMaterial.Builder setHowRelatedHref(String value) {
            mInstance.mHowRelatedHref = value;
            return this;
        }
        public RelatedMaterial.Builder setMediaLocatorUri(String value) {
            mInstance.mMediaLocatorUri = value;
            return this;
        }
        public RelatedMaterial.Builder setMediaLocatorContentType(String value) {
            mInstance.mMediaLocatorContentType = value;
            return this;
        }
        public RelatedMaterial build() {
            RelatedMaterial instance = new RelatedMaterial();
            instance.mHowRelatedHref = mInstance.mHowRelatedHref;
            instance.mMediaLocatorUri = mInstance.mMediaLocatorUri;
            instance.mMediaLocatorContentType = mInstance.mMediaLocatorContentType;
            return instance;
        }
    }
}
