package org.orbtv.dvbiclient;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.orbtv.dvbiclient.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = DatabaseHandler.class.getSimpleName();
    private static final int DB_VERSION = 22;
    private static final String DB_NAME = "dvbi_db";
    private static final String FOREIGN_KEY_PREFIX_SERVICE = "service_";
    private static final String FOREIGN_KEY_PREFIX_INSTANCE = "instance_";
    private static final String COLUMN_ID = "id";

    private static final String SERVICE_LISTS_TABLE = "service_lists";
    private static final String SERVICE_LISTS_COLUMN_NAME = "name";
    private static final String SERVICE_LISTS_COLUMN_PROVIDER = "provider";
    private static final String SERVICE_LISTS_COLUMN_UID = "uid";

    private static final String CONTENT_GUIDES_TABLE = "schedule_info_endpoints";
    private static final String CONTENT_GUIDES_COLUMN_CGSID = "cgsid";
    private static final String CONTENT_GUIDES_COLUMN_SCHEDUTE_INFO_URI = "schedule_info_uri";

    private static final String SERVICES_TABLE = "services";
    private static final String SERVICES_COLUMN_FOREIGN_KEY = "foreign_key";
    private static final String SERVICES_COLUMN_UNIQUE_IDENTIFIER = "unique_identifier";
    private static final String SERVICES_COLUMN_LCN = "lcn";
    private static final String SERVICES_COLUMN_PROVIDER = "provider";
    private static final String SERVICES_COLUMN_ADDITIONAL_PARAMS = "additional_params";
    private static final String SERVICES_COLUMN_SERVICE_TYPE = "service_type";
    private static final String SERVICES_COLUMN_CONTENT_GUIDE_CGSID = "guide_cgsid";
    private static final String SERVICES_COLUMN_PARENTAL_RATING = "parental_rating";
    private static final String SERVICES_COLUMN_CONTENT_GUIDE_SERVICE_REF = "content_guide_service_ref";

    private static final String SERVICE_INSTANCES_TABLE = "service_instances";
    private static final String SERVICE_INSTANCES_COLUMN_SERVICE_UID = "service_uid";
    private static final String SERVICE_INSTANCES_COLUMN_INDEX = "array_index";
    private static final String SERVICE_INSTANCES_COLUMN_PRIORITY = "priority";
    private static final String SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS = "delivery_params";
    private static final String SERVICE_INSTANCES_COLUMN_DELIVERY_TYPE = "delivery_type";

    private static final String RELATED_MATERIALS_TABLE = "related_materials";
    private static final String RELATED_MATERIALS_COLUMN_FOREIGN_KEY = "foreign_key";
    private static final String RELATED_MATERIALS_COLUMN_INDEX = "array_index";
    private static final String RELATED_MATERIALS_COLUMN_HOW_RELATED_HREF = "how_related_href";
    private static final String RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_URI = "media_locator_uri";
    private static final String RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE = "media_locator_content_type";

    private static final String AVAILABILITY_PERIOD_TABLE = "availability_periods";
    private static final String AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY = "foreign_key";
    private static final String AVAILABILITY_PERIOD_COLUMN_INDEX = "array_index";
    private static final String AVAILABILITY_PERIOD_COLUMN_VALID_FROM = "valid_from";
    private static final String AVAILABILITY_PERIOD_COLUMN_VALID_TO = "valid_to";

    private static final String AVAILABILITY_PERIOD_INTERVALS_TABLE = "availability_period_intervals";
    private static final String AVAILABILITY_PERIOD_INTERVALS_COLUMN_FOREIGN_KEY = "foreign_key";
    private static final String AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX = "array_index";
    private static final String AVAILABILITY_PERIOD_INTERVALS_COLUMN_START_TIME = "start_time";
    private static final String AVAILABILITY_PERIOD_INTERVALS_COLUMN_END_TIME = "end_time";
    private static final String AVAILABILITY_PERIOD_INTERVALS_COLUMN_DAYS = "days";

    private static final String SERVICE_NAMES_TABLE = "service_names";
    private static final String SERVICE_NAMES_COLUMN_SERVICE_UID = "service_uid";
    private static final String SERVICE_NAMES_COLUMN_NAME = "name";
    private static final String SERVICE_NAMES_COLUMN_COUNTRY = "country";

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SERVICE_LISTS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICE_LISTS_COLUMN_UID + " TEXT NOT NULL UNIQUE, "
                + SERVICE_LISTS_COLUMN_NAME + " TEXT, "
                + SERVICE_LISTS_COLUMN_PROVIDER + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + CONTENT_GUIDES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + CONTENT_GUIDES_COLUMN_CGSID + " TEXT NOT NULL UNIQUE, "
                + CONTENT_GUIDES_COLUMN_SCHEDUTE_INFO_URI + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICES_COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + SERVICES_COLUMN_UNIQUE_IDENTIFIER + " TEXT NOT NULL UNIQUE, "
                + SERVICES_COLUMN_LCN + " TEXT,"
                + SERVICES_COLUMN_PROVIDER + " TEXT,"
                + SERVICES_COLUMN_SERVICE_TYPE + " TEXT,"
                + SERVICES_COLUMN_CONTENT_GUIDE_CGSID + " TEXT,"
                + SERVICES_COLUMN_PARENTAL_RATING + " INTEGER,"
                + SERVICES_COLUMN_CONTENT_GUIDE_SERVICE_REF + " TEXT,"
                + SERVICES_COLUMN_ADDITIONAL_PARAMS + " BLOB)"
        );
        db.execSQL("CREATE TABLE " + SERVICE_INSTANCES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICE_INSTANCES_COLUMN_SERVICE_UID + " TEXT NOT NULL, "
                + SERVICE_INSTANCES_COLUMN_INDEX + " INTEGER NOT NULL, "
                + SERVICE_INSTANCES_COLUMN_PRIORITY + " INTEGER,"
                + SERVICE_INSTANCES_COLUMN_DELIVERY_TYPE + " TEXT,"
                + SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS + " BLOB)"
        );
        db.execSQL("CREATE TABLE " + RELATED_MATERIALS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + RELATED_MATERIALS_COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + RELATED_MATERIALS_COLUMN_INDEX + " INTEGER NOT NULL, "
                + RELATED_MATERIALS_COLUMN_HOW_RELATED_HREF + " TEXT, "
                + RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_URI + " TEXT, "
                + RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICE_NAMES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICE_NAMES_COLUMN_SERVICE_UID + " TEXT NOT NULL, "
                + SERVICE_NAMES_COLUMN_NAME + " TEXT, "
                + SERVICE_NAMES_COLUMN_COUNTRY + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + AVAILABILITY_PERIOD_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + AVAILABILITY_PERIOD_COLUMN_INDEX + " INTEGER NOT NULL, "
                + AVAILABILITY_PERIOD_COLUMN_VALID_FROM + " INTEGER, "
                + AVAILABILITY_PERIOD_COLUMN_VALID_TO + " INTEGER)"
        );
        db.execSQL("CREATE TABLE " + AVAILABILITY_PERIOD_INTERVALS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AVAILABILITY_PERIOD_INTERVALS_COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX + " INTEGER NOT NULL, "
                + AVAILABILITY_PERIOD_INTERVALS_COLUMN_DAYS + " TEXT, "
                + AVAILABILITY_PERIOD_INTERVALS_COLUMN_START_TIME + " INTEGER, "
                + AVAILABILITY_PERIOD_INTERVALS_COLUMN_END_TIME + " INTEGER)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Log.i(TAG, "Dropping database tables...");
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_INSTANCES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + CONTENT_GUIDES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_LISTS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + RELATED_MATERIALS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_NAMES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + AVAILABILITY_PERIOD_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + AVAILABILITY_PERIOD_INTERVALS_TABLE);
        // TODO: delete dvbi services from the android channel database
        onCreate(db);
    }

    public synchronized List<ServiceList> getServiceLists() {
        SQLiteDatabase db = getReadableDatabase();

        ArrayList<ServiceList> ret = new ArrayList<>();
        String[] projection = { SERVICE_LISTS_COLUMN_UID };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICE_LISTS_TABLE, projection, null, null, null, null, null);
            if (cursor != null)
            {
                ServiceList.Builder listBuilder = new ServiceList.Builder();
                while (cursor.moveToNext())
                {
                    ret.add(listBuilder
                            .setUID(cursor.getString(0))
                            .setServices(getServices(db, cursor.getString(0)))
                            .build());
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return ret;
    }

    public synchronized Service getServiceForUID(String uid) {
        Service service = null;
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = { SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER,
                SERVICES_COLUMN_LCN, SERVICES_COLUMN_ADDITIONAL_PARAMS, SERVICES_COLUMN_SERVICE_TYPE,
                SERVICES_COLUMN_CONTENT_GUIDE_CGSID, SERVICES_COLUMN_PARENTAL_RATING,
                SERVICES_COLUMN_CONTENT_GUIDE_SERVICE_REF
        };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection,
                    SERVICES_COLUMN_UNIQUE_IDENTIFIER + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
            {
                Triplet triplet = null;
                try {
                    String dvbUri = new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet");
                    if (dvbUri != null) {
                        triplet = Triplet.parseFromURI(dvbUri);
                    }
                }
                catch (Exception e) { }
                service = new Service.Builder()
                        .setServiceNames(getServiceNamesForUID(db, cursor.getString(1)))
                        .setProviderName(cursor.getString(0))
                        .setUniqueIdentifier(cursor.getString(1))
                        .setServiceType(cursor.getString(4))
                        .setLcnNumber(cursor.getString(2))
                        .setTriplet(triplet)
                        .setInstances(getServiceInstancesForUID(db, cursor.getString(1)))
                        .setRelatedMaterials(getRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + cursor.getString(1)))
                        .setContentGuideSource(getContendGuideForCGSID(db, cursor.getString(5)))
                        .setParentalRating(cursor.getInt(6))
                        .setContentGuideServiceRef(cursor.getString(7))
                        .build();
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return service;
    }

    public synchronized void updateServiceList(ServiceList serviceList) {
        SQLiteDatabase db = getWritableDatabase();
        String uids = "";
        String cgsids = "";
        ContentValues values = new ContentValues();
        values.put(SERVICE_LISTS_COLUMN_UID, serviceList.getUID());
        values.put(SERVICE_LISTS_COLUMN_NAME, "myname");
        values.put(SERVICE_LISTS_COLUMN_PROVIDER, "myprovider");

        if (db.update(SERVICE_LISTS_TABLE, values, SERVICE_LISTS_COLUMN_UID + "='" + serviceList.getUID() + "'", null) == 0) {
            db.insert(SERVICE_LISTS_TABLE, null, values);
            Log.d(TAG, "Adding service list " + serviceList.getUID());
        }
        else {
            Log.d(TAG, "Updating service list " + serviceList.getUID());
        }

        for (Service service : serviceList.getServices()) {
            String uid = service.getUniqueIdentifier();
            ContentGuide guide = service.getContentGuide();
            uids += ",'" + uid + "'";
            if (guide != null && !cgsids.contains(guide.getCGSID())) {
                cgsids += ",'" + guide.getCGSID() + "'";
            }

            updateService(db, serviceList.getUID(), service);
            for (int i = 0; i < service.getInstances().size(); ++i) {
                uids += ",'" + uid + "_" + i + "'";
            }
        }
        if (!uids.isEmpty()) {
            uids = uids.substring(1); // remove leading comma
            Log.i(TAG, "Deleting service and instances with UIDs not in " + uids);
            db.delete(SERVICES_TABLE, SERVICES_COLUMN_UNIQUE_IDENTIFIER + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_INSTANCES_TABLE, SERVICE_INSTANCES_COLUMN_SERVICE_UID + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_NAMES_TABLE, SERVICE_NAMES_COLUMN_SERVICE_UID + " NOT IN (" + uids + ")", null);
            // TODO: delete related materials for non-existent services/instances
        }
        if (!cgsids.isEmpty()) {
            cgsids = cgsids.substring(1); // remove leading comma
            Log.i(TAG, "Deleting content guides with CGSIDs not in " + cgsids);
            db.delete(CONTENT_GUIDES_TABLE, CONTENT_GUIDES_COLUMN_CGSID + " NOT IN (" + cgsids + ")", null);
        }
    }

    private List<Service> getServices(SQLiteDatabase db, String listUID) {
        ArrayList<Service> ret = new ArrayList<>();
        String[] projection = { SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER,
                SERVICES_COLUMN_LCN, SERVICES_COLUMN_ADDITIONAL_PARAMS, SERVICES_COLUMN_SERVICE_TYPE,
                SERVICES_COLUMN_CONTENT_GUIDE_CGSID, SERVICES_COLUMN_PARENTAL_RATING,
                SERVICES_COLUMN_CONTENT_GUIDE_SERVICE_REF
        };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection, SERVICES_COLUMN_FOREIGN_KEY + "='" + listUID + "'", null, null, null, null);
            if (cursor != null)
            {
                Service.Builder serviceBuilder = new Service.Builder();
                while (cursor.moveToNext())
                {
                    Triplet triplet = null;
                    try {
                        String dvbUri = new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet");
                        if (dvbUri != null) {
                            triplet = Triplet.parseFromURI(dvbUri);
                        }
                    }
                    catch (JSONException e) { }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    ret.add(serviceBuilder
                            .setServiceNames(getServiceNamesForUID(db, cursor.getString(1)))
                            .setProviderName(cursor.getString(0))
                            .setUniqueIdentifier(cursor.getString(1))
                            .setServiceType(cursor.getString(4))
                            .setLcnNumber(cursor.getString(2))
                            .setTriplet(triplet)
                            .setInstances(getServiceInstancesForUID(db, cursor.getString(1)))
                            .setRelatedMaterials(getRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + cursor.getString(1)))
                            .setContentGuideSource(getContendGuideForCGSID(db, cursor.getString(5)))
                            .setParentalRating(cursor.getInt(6))
                            .setContentGuideServiceRef(cursor.getString(7))
                            .build());
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return ret;
    }

    private ContentGuide getContendGuideForCGSID(SQLiteDatabase db, String cgsid) {
        ContentGuide guide = null;
        if (cgsid != null) {
            String[] projection = { CONTENT_GUIDES_COLUMN_SCHEDUTE_INFO_URI };
            Cursor cursor = null;
            try
            {
                cursor = db.query(CONTENT_GUIDES_TABLE, projection,
                        CONTENT_GUIDES_COLUMN_CGSID + "='" + cgsid + "'",
                        null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    guide = new ContentGuide.Builder()
                            .setCGSID(cgsid)
                            .setScheduleInfoEndpointURI(cursor.getString(0))
                            .build();
                }
            }
            finally
            {
                if (cursor != null)
                {
                    cursor.close();
                }
            }
            Log.i(TAG, "Content guide for cgsid '" + cgsid + "'\n" + guide);
        }
        return guide;
    }

    private void updateService(SQLiteDatabase db, String listUID, Service service) {
        String uid = service.getUniqueIdentifier();
        ContentValues values = new ContentValues();
        JSONObject params = new JSONObject();
        values.put(SERVICES_COLUMN_FOREIGN_KEY, listUID);
        values.put(SERVICES_COLUMN_PROVIDER, service.getProviderName());
        values.put(SERVICES_COLUMN_UNIQUE_IDENTIFIER, uid);
        values.put(SERVICES_COLUMN_LCN, service.getLCNNumber());
        values.put(SERVICES_COLUMN_SERVICE_TYPE, service.getServiceType());
        values.put(SERVICES_COLUMN_CONTENT_GUIDE_CGSID, (service.getContentGuide() == null ? null : service.getContentGuide().getCGSID()));
        if (service.getTriplet() != null) {
            try {
                params.put("hbbtv-i:DVBTriplet", service.getTriplet());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        values.put(SERVICES_COLUMN_ADDITIONAL_PARAMS, params.toString().getBytes(StandardCharsets.UTF_8));

        if (db.update(SERVICES_TABLE, values, SERVICES_COLUMN_UNIQUE_IDENTIFIER + "='" +
                uid + "' AND " + SERVICES_COLUMN_FOREIGN_KEY + "='" + listUID + "'", null) == 0) {
            db.insert(SERVICES_TABLE, null, values);
            Log.d(TAG, "Adding service " + uid);
        }
        else {
            Log.d(TAG, "Updating service " + uid);
        }

        updateContentGuide(db, service.getContentGuide());
        updateRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + uid, service.getRelatedMaterials());
        updateServiceNames(db, uid, service.getDisplayNames());
        updateServiceInstances(db, uid, service.getInstances());
    }

    private void updateServiceInstances(SQLiteDatabase db, String uid, List<ServiceInstance> instances) {
        for (int i = 0; i < instances.size(); ++i) {
            ServiceInstance instance = instances.get(i);
            JSONObject params = new JSONObject();
            ContentValues values = new ContentValues();
            values.put(SERVICE_INSTANCES_COLUMN_SERVICE_UID, uid);
            values.put(SERVICE_INSTANCES_COLUMN_PRIORITY, instance.getPriority());
            values.put(SERVICE_INSTANCES_COLUMN_INDEX, i);
            values.put(SERVICE_INSTANCES_COLUMN_DELIVERY_TYPE, instance.getDeliveryType());
            for (Map.Entry<String,String> entry : instance.getDeliveryParameters().entrySet()) {
                try {
                    params.put(entry.getKey(), entry.getValue());
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            values.put(SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS, params.toString().getBytes(StandardCharsets.UTF_8));
            // in case there are more services than before, insert them
            if (db.update(SERVICE_INSTANCES_TABLE, values,
                    SERVICE_INSTANCES_COLUMN_SERVICE_UID + "='" + uid + "' AND "
                            + SERVICE_INSTANCES_COLUMN_INDEX + "=" + i, null) == 0) {
                db.insert(SERVICE_INSTANCES_TABLE, null, values);
            }
            updateServiceNames(db, uid + "_" + i, instance.getDisplayNames());
            updateRelatedMaterials(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + i, instance.getRelatedMaterials());

            List<AvailabilityPeriod> availabilityPeriods = instance.getAvailabilityPeriods();
            if (availabilityPeriods != null) {
                updateAvailabilityPeriods(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + i, availabilityPeriods);
            }
        }

        // in case there are less service instances than before, delete those that have
        // higher index than the size of the current list
        db.delete(SERVICE_INSTANCES_TABLE, SERVICE_INSTANCES_COLUMN_SERVICE_UID
                + "='" + uid + "' AND " + SERVICE_INSTANCES_COLUMN_INDEX + ">="
                + instances.size(), null);
    }

    private void updateContentGuide(SQLiteDatabase db, ContentGuide guide) {
        if (guide != null) {
            ContentValues values = new ContentValues();
            values.put(CONTENT_GUIDES_COLUMN_CGSID, guide.getCGSID());
            values.put(CONTENT_GUIDES_COLUMN_SCHEDUTE_INFO_URI, guide.getScheduleInfoEndpointURI());
            if (db.update(CONTENT_GUIDES_TABLE, values, CONTENT_GUIDES_COLUMN_CGSID
                    + "='" + guide.getCGSID() + "'", null) == 0) {
                db.insert(CONTENT_GUIDES_TABLE, null, values);
            }
        }
    }

    private void updateRelatedMaterials(SQLiteDatabase db, String foreignKey, List<RelatedMaterial> materials) {
        for (int i = 0; i < materials.size(); ++i) {
            RelatedMaterial material = materials.get(i);
            ContentValues values = new ContentValues();
            values.put(RELATED_MATERIALS_COLUMN_FOREIGN_KEY, foreignKey);
            values.put(RELATED_MATERIALS_COLUMN_INDEX, i);
            values.put(RELATED_MATERIALS_COLUMN_HOW_RELATED_HREF, material.getHowRelatedHref());
            values.put(RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_URI, material.getMediaLocatorUri());
            values.put(RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE, material.getMediaLocatorContentType());
            if (db.update(RELATED_MATERIALS_TABLE, values, RELATED_MATERIALS_COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + RELATED_MATERIALS_COLUMN_INDEX
                    + "=" + i, null) == 0) {
                db.insert(RELATED_MATERIALS_TABLE, null, values);
            }
        }
        // in case there are less related materials than before, delete those that have
        // higher index than the size of the current list
        db.delete(RELATED_MATERIALS_TABLE, RELATED_MATERIALS_COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + RELATED_MATERIALS_COLUMN_INDEX
                + ">=" + materials.size(), null);
    }

    private void updateAvailabilityPeriods(SQLiteDatabase db, String foreignKey, List<AvailabilityPeriod> availabilityPeriods) {
        for (int i = 0; i < availabilityPeriods.size(); i++) {
            ContentValues values = new ContentValues();
            AvailabilityPeriod period = availabilityPeriods.get(i);
            values.put(AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY, foreignKey);
            values.put(AVAILABILITY_PERIOD_COLUMN_VALID_FROM, period.getValidFrom());
            values.put(AVAILABILITY_PERIOD_COLUMN_VALID_TO, period.getValidTo());
            values.put(AVAILABILITY_PERIOD_COLUMN_INDEX, i);
            if (db.update(AVAILABILITY_PERIOD_TABLE, values, AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + AVAILABILITY_PERIOD_COLUMN_INDEX + "=" + i, null) == 0) {
                db.insert(AVAILABILITY_PERIOD_TABLE, null, values);
            }
            updateAvailabilityPeriodIntervals(db, foreignKey + "_" + i, period.getIntervals());
        }
        // in case there are less availability periods than before, delete those that have
        // higher index than the size of the current list
        db.delete(AVAILABILITY_PERIOD_TABLE, AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + AVAILABILITY_PERIOD_COLUMN_INDEX
                + ">=" + availabilityPeriods.size(), null);
    }

    private void updateAvailabilityPeriodIntervals(SQLiteDatabase db, String foreignKey, List<AvailabilityPeriod.Interval> intervals) {
        for (int i = 0; i < intervals.size(); i++) {
            ContentValues values = new ContentValues();
            values.put(AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY, foreignKey);
            values.put(AVAILABILITY_PERIOD_INTERVALS_COLUMN_START_TIME, intervals.get(i).getStartTime());
            values.put(AVAILABILITY_PERIOD_INTERVALS_COLUMN_END_TIME, intervals.get(i).getEndTime());
            values.put(AVAILABILITY_PERIOD_INTERVALS_COLUMN_DAYS, intervals.get(i).getDays());
            values.put(AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX, i);
            if (db.update(AVAILABILITY_PERIOD_INTERVALS_TABLE, values, AVAILABILITY_PERIOD_INTERVALS_COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX
                    + "=" + i, null) == 0) {
                db.insert(AVAILABILITY_PERIOD_INTERVALS_TABLE, null, values);
            }
        }
        // in case there are less availability period intervals than before, delete those that have
        // higher index than the size of the current list
        db.delete(AVAILABILITY_PERIOD_INTERVALS_TABLE, AVAILABILITY_PERIOD_INTERVALS_COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX
                + ">=" + intervals.size(), null);
    }

    private List<RelatedMaterial> getRelatedMaterials(SQLiteDatabase db, String foreignKey) {
        ArrayList<RelatedMaterial> materials = new ArrayList<>();
        String[] projection = { RELATED_MATERIALS_COLUMN_HOW_RELATED_HREF,
                RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_URI, RELATED_MATERIALS_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(RELATED_MATERIALS_TABLE, projection,
                    RELATED_MATERIALS_COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, RELATED_MATERIALS_COLUMN_INDEX);
            if (cursor != null) {
                RelatedMaterial.Builder relMatBuilder = new RelatedMaterial.Builder();
                while (cursor.moveToNext()) {
                    materials.add (relMatBuilder
                            .setHowRelatedHref(cursor.getString(0))
                            .setMediaLocatorUri(cursor.getString(1))
                            .setMediaLocatorContentType(cursor.getString(2))
                            .build());
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return materials;
    }

    private List<AvailabilityPeriod> getAvailabilityPeriods(SQLiteDatabase db, String foreignKey) {
        String[] projection = { AVAILABILITY_PERIOD_COLUMN_VALID_FROM, AVAILABILITY_PERIOD_COLUMN_VALID_TO, AVAILABILITY_PERIOD_COLUMN_INDEX };
        ArrayList<AvailabilityPeriod> periods = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(AVAILABILITY_PERIOD_TABLE, projection,
                    AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, AVAILABILITY_PERIOD_COLUMN_INDEX);
            if (cursor != null) {
                while(cursor.moveToNext()) {
                    periods.add(new AvailabilityPeriod.Builder()
                            .setValidFrom(cursor.isNull(0) ? null : cursor.getLong(0))
                            .setValidTo(cursor.isNull(1) ? null : cursor.getLong(1))
                            .setIntervals(getAvailabilityPeriodIntervals(db, foreignKey + "_" + cursor.getInt(2)))
                            .build());
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return periods;
    }

    private List<AvailabilityPeriod.Interval> getAvailabilityPeriodIntervals(SQLiteDatabase db, String foreignKey) {
        List<AvailabilityPeriod.Interval> intervals = new ArrayList<>();
        String[] projection = { AVAILABILITY_PERIOD_INTERVALS_COLUMN_START_TIME, AVAILABILITY_PERIOD_INTERVALS_COLUMN_END_TIME, AVAILABILITY_PERIOD_INTERVALS_COLUMN_DAYS };
        Cursor cursor = null;
        try {
            cursor = db.query(AVAILABILITY_PERIOD_INTERVALS_TABLE, projection,
                    AVAILABILITY_PERIOD_INTERVALS_COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, AVAILABILITY_PERIOD_INTERVALS_COLUMN_INDEX);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    intervals.add(new AvailabilityPeriod.Interval.Builder()
                            .setStartTime(cursor.isNull(0) ? null : cursor.getInt(0))
                            .setEndTime(cursor.isNull(0) ? null : cursor.getInt(1))
                            .setDays(cursor.getString(2))
                            .build());
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return intervals;
    }

    private List<ServiceInstance> getServiceInstancesForUID(SQLiteDatabase db, String uid) {
        ArrayList<ServiceInstance> instances = new ArrayList<>();
        String[] projection = { SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS, SERVICE_INSTANCES_COLUMN_PRIORITY,
                SERVICE_INSTANCES_COLUMN_INDEX, SERVICE_INSTANCES_COLUMN_DELIVERY_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICE_INSTANCES_TABLE, projection,
                    SERVICE_INSTANCES_COLUMN_SERVICE_UID + "='" + uid + "'",
                    null, null, null, SERVICE_INSTANCES_COLUMN_INDEX);
            if (cursor != null)
            {
                ServiceInstance.Builder instanceBuilder = new ServiceInstance.Builder();
                while (cursor.moveToNext()) {
                    Triplet triplet = null;
                    Map<String, String> deliveryParams = null;
                    try {
                        deliveryParams = parseDeliveryParameters(new JSONObject(new String(cursor.getBlob(0))));
                        triplet = Triplet.parseFromURI(deliveryParams.get("DVBTriplet"));
                    } catch (Exception e) { }
                    instances.add(instanceBuilder
                                    .setDisplayNames(getServiceNamesForUID(db, uid + "_" + instances.size()))
                                    .setPriority(cursor.getInt(1))
                                    .setDeliveryType(cursor.getString(3))
                                    .setDeliveryParameters(deliveryParams)
                                    .setTriplet(triplet)
                                    .setRelatedMaterials(getRelatedMaterials(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + cursor.getInt(2)))
                                    .setAvailabilityPeriods(getAvailabilityPeriods(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + cursor.getInt(2)))
                                    .build());
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return instances;
    }

    private void updateServiceNames(SQLiteDatabase db, String uid, Map<String, String> names) {
        db.delete(SERVICE_NAMES_TABLE, SERVICE_NAMES_COLUMN_SERVICE_UID + " = '" + uid + "'", null);
        for (Map.Entry<String, String> entry : names.entrySet()) {
            ContentValues values = new ContentValues();
            values.put(SERVICE_NAMES_COLUMN_SERVICE_UID, uid);
            values.put(SERVICE_NAMES_COLUMN_COUNTRY, entry.getKey());
            values.put(SERVICE_NAMES_COLUMN_NAME, entry.getValue());
            db.insert(SERVICE_NAMES_TABLE, null, values);
        }
    }

    private Map<String, String> getServiceNamesForUID(SQLiteDatabase db, String uid) {
        Map<String, String> names = new HashMap<>();
        String[] projection = { SERVICE_NAMES_COLUMN_COUNTRY, SERVICE_NAMES_COLUMN_NAME };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICE_NAMES_TABLE, projection,
                    SERVICE_NAMES_COLUMN_SERVICE_UID + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null)
            {
                while (cursor.moveToNext()) {
                    names.put(cursor.getString(0), cursor.getString(1));
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return names;
    }

    private static Map<String, String> parseDeliveryParameters(JSONObject deliveryParams) {
        Iterator<String> keys = deliveryParams.keys();
        HashMap<String, String> result = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                result.put(key, deliveryParams.get(key).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
