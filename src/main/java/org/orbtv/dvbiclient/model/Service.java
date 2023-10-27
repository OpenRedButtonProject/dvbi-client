package org.orbtv.dvbiclient.model;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Service implements IService {
    private static final String TAG = Service.class.getSimpleName();
    private String mUniqueIdentifier;
    private String mProviderName;
    private String mServiceType;
    private Triplet mTriplet;
    private List<RelatedMaterial> mRelatedMaterials = new ArrayList<>();
    private List<ServiceInstance> mInstances = new ArrayList<>();
    private ContentGuide mContentGuideSource;
    private String mContentGuideSourceRef;
    private String mContentGuideServiceRef;
    private Integer mParentalRating;
    private Map<String, String> mServiceNames = new HashMap<>();
    private String mLcnNumber;

    private Service() { }

    public void updateContentGuide(List<ContentGuide> guides) {
        if (guides != null && !guides.isEmpty()) {
            if (mContentGuideSource == null && mContentGuideSourceRef != null) {
                for (ContentGuide guide : guides) {
                    if (mContentGuideSourceRef.equals(guide.getCGSID())) {
                        mContentGuideSource = guide;
                        break;
                    }
                }
            }

            if (mContentGuideSource == null && mContentGuideServiceRef != null) {
                mContentGuideSource = guides.get(0);
            }
        }
    }

    public String getUniqueIdentifier() { return mUniqueIdentifier; }
    
    public Map<String, String> getDisplayNames() { return mServiceNames; }

    public String getProviderName() { return mProviderName; }
 
    public String getServiceType() { return mServiceType; }

    public List<RelatedMaterial> getRelatedMaterials() { return mRelatedMaterials; }

    public synchronized List<ServiceInstance> getInstances() { return mInstances; }

    public Triplet getTriplet() { return mTriplet; }

    public void setLCNNumber(String lcnNumber) { this.mLcnNumber = lcnNumber; }

    public String getLCNNumber() { return mLcnNumber; }

    public ContentGuide getContentGuide() { return mContentGuideSource; }

    public Integer getParentalRating() { return mParentalRating; }

    public String getContentGuideServiceRef() { return mContentGuideServiceRef; }

    @Override
    public String toString() {
        String ret = "- " + this.getClass().getSimpleName() + " " + mUniqueIdentifier + " -"
                + "\nserviceNames: " + this.mServiceNames
                + "\nlcn: " + this.mLcnNumber
                + "\ntriplet: " + this.mTriplet;
        for (RelatedMaterial mat : mRelatedMaterials) {
            ret += "\n" + mat.toString();
        }
        for (ServiceInstance instance : mInstances) {
            ret += "\n" + instance.toString();
        }

        if(mContentGuideSource != null) {
            ret += "\n" + mContentGuideSource.toString();
        }
        return ret;
    }

    public static List<Service> parseFromXML(XmlPullParser xpp) throws Exception {
        List<Service> services = new ArrayList<>();
        int eventType = xpp.getEventType();
        Service currentService = null;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("Service".equals(xpp.getName())) {
                    currentService = new Service();
                    services.add(currentService);
                } else if (currentService != null) {
                    switch (xpp.getName()) {
                        case "ContentGuideSource":
                            currentService.mContentGuideSource = parseContentGuideSource(xpp);
                            break;
                        case "ContentGuideSourceRef":
                            currentService.mContentGuideSourceRef = xpp.nextText();
                            break;
                        case "ContentGuideServiceRef":
                            currentService.mContentGuideServiceRef = xpp.nextText();
                            break;
                        case "ParentalRating":
                            currentService.mParentalRating = Integer.parseInt(xpp.nextText());
                            break;
                        case "UniqueIdentifier":
                            currentService.mUniqueIdentifier = xpp.nextText();
                            break;
                        case "ServiceName":
                            String language = xpp.getAttributeValue(null, "lang");
                            String name = xpp.nextText();
                            currentService.mServiceNames.put(language, name);
                            break;
                        case "ServiceType":
                            currentService.mServiceType = xpp.getAttributeValue(null, "href");
                            break;
                        case "ProviderName":
                            currentService.mProviderName = xpp.nextText();
                            break;
                        case "RelatedMaterial":
                            RelatedMaterial relatedMaterial = RelatedMaterial.parseFromXML(xpp);
                            currentService.mRelatedMaterials.add(relatedMaterial);
                            break;
                        case "AdditionalServiceParameters":
                            parseAdditionalParameters(currentService, xpp);
                            break;
                        case "ServiceInstance":
                            ServiceInstance instance = ServiceInstance.parseFromXML(xpp);
                            currentService.mInstances.add(instance);
                            break;
                    }
                }
            }
            eventType = xpp.next();
        }
        return services;
    }

    private static void parseAdditionalParameters(Service service, XmlPullParser xpp) throws Exception {
        String extensionName = xpp.getAttributeValue(null, "extensionName");
        if (extensionName != null && extensionName.equals("urn:hbbtv:dvbi:service:serviceIdentifierTriplet")) {
            service.mTriplet = Triplet.parseFromXML(xpp);
        }
    }

    private static ContentGuide parseContentGuideSource(XmlPullParser xpp) {
        List<ContentGuide> contentGuideSource = null;
        try {
            contentGuideSource = ContentGuide.parseSourceFromXML(xpp);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing XML", e);
            return null;
        }

        if(contentGuideSource != null && contentGuideSource.size() == 1) { //size can be >1 only at service list level
            return contentGuideSource.get(0);
        }
        else {
            Log.e(TAG, "Error Parsing content guide source");
            return null;
        }
    }

    public static class Builder {
        private Service mInstance;
        public Builder() {
            mInstance = new Service();
        }
        public Service.Builder setUniqueIdentifier(String value) {
            mInstance.mUniqueIdentifier = value;
            return this;
        }
        public Service.Builder setProviderName(String value) {
            mInstance.mProviderName = value;
            return this;
        }
        public Service.Builder setServiceType(String value) {
            mInstance.mServiceType = value;
            return this;
        }
        public Service.Builder setTriplet(Triplet value) {
            mInstance.mTriplet = value;
            return this;
        }
        public Service.Builder setRelatedMaterials(List<RelatedMaterial> value) {
            mInstance.mRelatedMaterials = value;
            return this;
        }
        public Service.Builder setInstances(List<ServiceInstance> value) {
            mInstance.mInstances = value;
            return this;
        }
        public Service.Builder setContentGuideSource(ContentGuide value) {
            mInstance.mContentGuideSource = value;
            return this;
        }
        public Service.Builder setContentGuideSourceRef(String value) {
            mInstance.mContentGuideSourceRef = value;
            return this;
        }
        public Service.Builder setContentGuideServiceRef(String value) {
            mInstance.mContentGuideServiceRef = value;
            return this;
        }
        public Service.Builder setParentalRating(int value) {
            mInstance.mParentalRating = value;
            return this;
        }
        public Service.Builder setServiceNames(Map<String, String> value) {
            mInstance.mServiceNames = value;
            return this;
        }
        public Service.Builder setLcnNumber(String value) {
            mInstance.mLcnNumber = value;
            return this;
        }
        public Service build() {
            Service instance = new Service();
            instance.mUniqueIdentifier = mInstance.mUniqueIdentifier;
            instance.mProviderName = mInstance.mProviderName;
            instance.mServiceType = mInstance.mServiceType;
            instance.mTriplet = mInstance.mTriplet;
            instance.mRelatedMaterials = mInstance.mRelatedMaterials;
            instance.mInstances = mInstance.mInstances;
            instance.mContentGuideSource = mInstance.mContentGuideSource;
            instance.mContentGuideSourceRef = mInstance.mContentGuideSourceRef;
            instance.mContentGuideServiceRef = mInstance.mContentGuideServiceRef;
            instance.mParentalRating = mInstance.mParentalRating;
            instance.mServiceNames = mInstance.mServiceNames;
            instance.mLcnNumber = mInstance.mLcnNumber;
            return instance;
        }
    }
}

