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
    private static final int DB_VERSION = 25;
    private static final String DB_NAME = "dvbi_db";
    private static final String FOREIGN_KEY_PREFIX_SERVICE = "service_";
    private static final String FOREIGN_KEY_PREFIX_INSTANCE = "instance_";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_FOREIGN_KEY = "foreign_key";
    private static final String COLUMN_INDEX = "array_index";

    private static final String SERVICE_LISTS_TABLE = "service_lists";
    private static final String CONTENT_GUIDES_TABLE = "schedule_info_endpoints";
    private static final String SERVICES_TABLE = "services";
    private static final String SERVICE_INSTANCES_TABLE = "service_instances";
    private static final String RELATED_MATERIALS_TABLE = "related_materials";
    private static final String AVAILABILITY_PERIOD_TABLE = "availability_periods";
    private static final String AVAILABILITY_PERIOD_INTERVALS_TABLE = "availability_period_intervals";
    private static final String PROGRAMMES_TABLE = "programmes";

    private static final String SERVICE_NAMES_TABLE = "service_names";
    private static final String SERVICE_NAMES_COLUMN_NAME = "name";
    private static final String SERVICE_NAMES_COLUMN_COUNTRY = "country";

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SERVICE_LISTS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ServiceList.DB_COLUMN_UID + " TEXT NOT NULL UNIQUE, "
                + ServiceList.DB_COLUMN_NAME + " TEXT, "
                + ServiceList.DB_COLUMN_PROVIDER + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + CONTENT_GUIDES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ContentGuide.DB_COLUMN_CGSID + " TEXT NOT NULL UNIQUE, "
                + ContentGuide.DB_COLUMN_SCHEDULE_INFO_URI + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + Service.DB_COLUMN_UNIQUE_IDENTIFIER + " TEXT NOT NULL UNIQUE, "
                + Service.DB_COLUMN_LCN + " TEXT,"
                + Service.DB_COLUMN_PROVIDER + " TEXT,"
                + Service.DB_COLUMN_SERVICE_TYPE + " TEXT,"
                + Service.DB_COLUMN_CONTENT_GUIDE_CGSID + " TEXT,"
                + Service.DB_COLUMN_PARENTAL_RATING + " INTEGER,"
                + Service.DB_COLUMN_CONTENT_GUIDE_SERVICE_REF + " TEXT,"
                + Service.DB_COLUMN_ADDITIONAL_PARAMS + " BLOB)"
        );
        db.execSQL("CREATE TABLE " + SERVICE_INSTANCES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + COLUMN_INDEX + " INTEGER NOT NULL, "
                + ServiceInstance.DB_COLUMN_PRIORITY + " INTEGER,"
                + ServiceInstance.DB_COLUMN_DELIVERY_TYPE + " TEXT,"
                + ServiceInstance.DB_COLUMN_DELIVERY_PARAMS + " BLOB)"
        );
        db.execSQL("CREATE TABLE " + RELATED_MATERIALS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + COLUMN_INDEX + " INTEGER NOT NULL, "
                + RelatedMaterial.DB_COLUMN_HOW_RELATED_HREF + " TEXT, "
                + RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_URI + " TEXT, "
                + RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICE_NAMES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + SERVICE_NAMES_COLUMN_NAME + " TEXT, "
                + SERVICE_NAMES_COLUMN_COUNTRY + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + AVAILABILITY_PERIOD_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + COLUMN_INDEX + " INTEGER NOT NULL, "
                + AvailabilityPeriod.DB_COLUMN_VALID_FROM + " INTEGER, "
                + AvailabilityPeriod.DB_COLUMN_VALID_TO + " INTEGER)"
        );
        db.execSQL("CREATE TABLE " + AVAILABILITY_PERIOD_INTERVALS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + COLUMN_INDEX + " INTEGER NOT NULL, "
                + AvailabilityPeriod.Interval.DB_COLUMN_DAYS + " TEXT, "
                + AvailabilityPeriod.Interval.DB_COLUMN_START_TIME + " INTEGER, "
                + AvailabilityPeriod.Interval.DB_COLUMN_END_TIME + " INTEGER)"
        );
        db.execSQL("CREATE TABLE " + PROGRAMMES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FOREIGN_KEY + " TEXT NOT NULL, "
                + Programme.DB_COLUMN_TITLE + " TEXT, "
                + Programme.DB_COLUMN_SHORT_DESCRIPTION + " TEXT, "
                + Programme.DB_COLUMN_MEDIUM_DESCRIPTION + " TEXT, "
                + Programme.DB_COLUMN_LONG_DESCRIPTION + " TEXT, "
                + Programme.DB_COLUMN_START_TIME + " INTEGER NOT NULL, "
                + Programme.DB_COLUMN_END_TIME + " INTEGER NOT NULL, "
                + Programme.DB_COLUMN_PARENTAL_RATING + " INTEGER)"
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
        db.execSQL("DROP TABLE IF EXISTS " + PROGRAMMES_TABLE);
        // TODO: delete dvbi services from the android channel database
        onCreate(db);
    }

    public synchronized List<ServiceList> getServiceLists() {
        SQLiteDatabase db = getReadableDatabase();

        ArrayList<ServiceList> ret = new ArrayList<>();
        String[] projection = { ServiceList.DB_COLUMN_UID };
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
        String[] projection = { Service.DB_COLUMN_PROVIDER, Service.DB_COLUMN_UNIQUE_IDENTIFIER,
                Service.DB_COLUMN_LCN, Service.DB_COLUMN_ADDITIONAL_PARAMS, Service.DB_COLUMN_SERVICE_TYPE,
                Service.DB_COLUMN_CONTENT_GUIDE_CGSID, Service.DB_COLUMN_PARENTAL_RATING,
                Service.DB_COLUMN_CONTENT_GUIDE_SERVICE_REF
        };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection,
                    Service.DB_COLUMN_UNIQUE_IDENTIFIER + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
            {
                Triplet triplet = null;
                try {
                    triplet = Triplet.parseFromURI(new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet"));
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
        ContentValues values = serviceList.toContentValues();

        if (db.update(SERVICE_LISTS_TABLE, values, ServiceList.DB_COLUMN_UID + "='" + serviceList.getUID() + "'", null) == 0) {
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
            db.delete(SERVICES_TABLE, Service.DB_COLUMN_UNIQUE_IDENTIFIER + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_INSTANCES_TABLE, COLUMN_FOREIGN_KEY + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_NAMES_TABLE, COLUMN_FOREIGN_KEY + " NOT IN (" + uids + ")", null);
            db.delete(PROGRAMMES_TABLE, COLUMN_FOREIGN_KEY + " NOT IN (" + uids + ")", null);
            // TODO: delete related materials for non-existent services/instances
        }
        if (!cgsids.isEmpty()) {
            cgsids = cgsids.substring(1); // remove leading comma
            Log.i(TAG, "Deleting content guides with CGSIDs not in " + cgsids);
            db.delete(CONTENT_GUIDES_TABLE, ContentGuide.DB_COLUMN_CGSID + " NOT IN (" + cgsids + ")", null);
        }
    }

    public synchronized void updateProgrammesForService(String serviceUID, List<Programme> programmes) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(PROGRAMMES_TABLE, COLUMN_FOREIGN_KEY
                + "='" + serviceUID + "'", null);
        for (Programme programme : programmes) {
            ContentValues values = programme.toContentValues();
            values.put(COLUMN_FOREIGN_KEY, serviceUID);
            db.insert(PROGRAMMES_TABLE, null, values);
            Log.d(TAG, "Updated program: " + programme.getTitle());
        }
    }

    public synchronized List<Programme> getProgrammesForService(String serviceUID, long startTime, long endTime, Integer limit) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<Programme> programmes = new ArrayList<>();
        String[] projection = { Programme.DB_COLUMN_TITLE, Programme.DB_COLUMN_SHORT_DESCRIPTION, Programme.DB_COLUMN_MEDIUM_DESCRIPTION,
                Programme.DB_COLUMN_LONG_DESCRIPTION, Programme.DB_COLUMN_PARENTAL_RATING, Programme.DB_COLUMN_START_TIME,
                Programme.DB_COLUMN_END_TIME };
        String start = String.valueOf(startTime);
        String end = String.valueOf(endTime);
        String[] selectionArgs = { serviceUID, start, start, end, end, start, end, limit.toString() };
        Cursor cursor = null;
        try
        {
            cursor = db.query(PROGRAMMES_TABLE, projection,
                    COLUMN_FOREIGN_KEY + "= ? AND (((" +
                            Programme.DB_COLUMN_START_TIME + " >= ? OR " + Programme.DB_COLUMN_END_TIME + " >= ?) AND (" +
                            Programme.DB_COLUMN_START_TIME + " <= ? OR " + Programme.DB_COLUMN_END_TIME + " <= ?)) OR (" +
                            Programme.DB_COLUMN_START_TIME + " <= ? AND " + Programme.DB_COLUMN_END_TIME + " >= ?)) ORDER BY " +
                            Programme.DB_COLUMN_START_TIME + " LIMIT ?",
                    selectionArgs, null, null, null);
            if (cursor != null) {
                Programme.Builder builder = new Programme.Builder();
                while (cursor.moveToNext()) {
                    programmes.add(builder
                            .setTitle(cursor.getString(0))
                            .setShortDescription(cursor.getString(1))
                            .setMediumDescription(cursor.getString(2))
                            .setLongDescription(cursor.getString(3))
                            .setParentalRating(cursor.getInt(4))
                            .setStartTime(cursor.getLong(5))
                            .setEndTime(cursor.getLong(6))
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
        return programmes;
    }

    private List<Service> getServices(SQLiteDatabase db, String listUID) {
        ArrayList<Service> ret = new ArrayList<>();
        String[] projection = { Service.DB_COLUMN_PROVIDER, Service.DB_COLUMN_UNIQUE_IDENTIFIER,
                Service.DB_COLUMN_LCN, Service.DB_COLUMN_ADDITIONAL_PARAMS, Service.DB_COLUMN_SERVICE_TYPE,
                Service.DB_COLUMN_CONTENT_GUIDE_CGSID, Service.DB_COLUMN_PARENTAL_RATING,
                Service.DB_COLUMN_CONTENT_GUIDE_SERVICE_REF
        };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection, COLUMN_FOREIGN_KEY + "='" + listUID + "'", null, null, null, null);
            if (cursor != null)
            {
                Service.Builder serviceBuilder = new Service.Builder();
                while (cursor.moveToNext())
                {
                    Triplet triplet = null;
                    try {
                        triplet = Triplet.parseFromURI(new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet"));
                    }
                    catch (Exception e) { }
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
            String[] projection = { ContentGuide.DB_COLUMN_SCHEDULE_INFO_URI };
            Cursor cursor = null;
            try
            {
                cursor = db.query(CONTENT_GUIDES_TABLE, projection,
                        ContentGuide.DB_COLUMN_CGSID + "='" + cgsid + "'",
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
        ContentValues values = service.toContentValues();
        values.put(COLUMN_FOREIGN_KEY, listUID);
        if (db.update(SERVICES_TABLE, values, Service.DB_COLUMN_UNIQUE_IDENTIFIER + "='" +
                uid + "' AND " + COLUMN_FOREIGN_KEY + "='" + listUID + "'", null) == 0) {
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
            ContentValues values = instance.toContentValues();
            values.put(COLUMN_FOREIGN_KEY, uid);
            values.put(COLUMN_INDEX, i);
            // in case there are more services than before, insert them
            if (db.update(SERVICE_INSTANCES_TABLE, values,
                    COLUMN_FOREIGN_KEY + "='" + uid + "' AND "
                            + COLUMN_INDEX + "=" + i, null) == 0) {
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
        db.delete(SERVICE_INSTANCES_TABLE, COLUMN_FOREIGN_KEY
                + "='" + uid + "' AND " + COLUMN_INDEX + ">="
                + instances.size(), null);
    }

    private void updateContentGuide(SQLiteDatabase db, ContentGuide guide) {
        if (guide != null) {
            ContentValues values = guide.toContentValues();
            if (db.update(CONTENT_GUIDES_TABLE, values, ContentGuide.DB_COLUMN_CGSID
                    + "='" + guide.getCGSID() + "'", null) == 0) {
                db.insert(CONTENT_GUIDES_TABLE, null, values);
            }
        }
    }

    private void updateRelatedMaterials(SQLiteDatabase db, String foreignKey, List<RelatedMaterial> materials) {
        for (int i = 0; i < materials.size(); ++i) {
            RelatedMaterial material = materials.get(i);
            ContentValues values = material.toContentValues();
            values.put(COLUMN_FOREIGN_KEY, foreignKey);
            values.put(COLUMN_INDEX, i);
            if (db.update(RELATED_MATERIALS_TABLE, values, COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + COLUMN_INDEX
                    + "=" + i, null) == 0) {
                db.insert(RELATED_MATERIALS_TABLE, null, values);
            }
        }
        // in case there are less related materials than before, delete those that have
        // higher index than the size of the current list
        db.delete(RELATED_MATERIALS_TABLE, COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + COLUMN_INDEX
                + ">=" + materials.size(), null);
    }

    private void updateAvailabilityPeriods(SQLiteDatabase db, String foreignKey, List<AvailabilityPeriod> availabilityPeriods) {
        for (int i = 0; i < availabilityPeriods.size(); i++) {
            AvailabilityPeriod period = availabilityPeriods.get(i);
            ContentValues values = period.toContentValues();
            values.put(COLUMN_FOREIGN_KEY, foreignKey);
            values.put(COLUMN_INDEX, i);
            if (db.update(AVAILABILITY_PERIOD_TABLE, values, COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + COLUMN_INDEX + "=" + i, null) == 0) {
                db.insert(AVAILABILITY_PERIOD_TABLE, null, values);
            }
            updateAvailabilityPeriodIntervals(db, foreignKey + "_" + i, period.getIntervals());
        }
        // in case there are less availability periods than before, delete those that have
        // higher index than the size of the current list
        db.delete(AVAILABILITY_PERIOD_TABLE, COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + COLUMN_INDEX
                + ">=" + availabilityPeriods.size(), null);
    }

    private void updateAvailabilityPeriodIntervals(SQLiteDatabase db, String foreignKey, List<AvailabilityPeriod.Interval> intervals) {
        for (int i = 0; i < intervals.size(); i++) {
            ContentValues values = intervals.get(i).toContentValues();
            values.put(COLUMN_FOREIGN_KEY, foreignKey);
            values.put(COLUMN_INDEX, i);
            if (db.update(AVAILABILITY_PERIOD_INTERVALS_TABLE, values, COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + COLUMN_INDEX
                    + "=" + i, null) == 0) {
                db.insert(AVAILABILITY_PERIOD_INTERVALS_TABLE, null, values);
            }
        }
        // in case there are less availability period intervals than before, delete those that have
        // higher index than the size of the current list
        db.delete(AVAILABILITY_PERIOD_INTERVALS_TABLE, COLUMN_FOREIGN_KEY
                + "='" + foreignKey + "' AND " + COLUMN_INDEX
                + ">=" + intervals.size(), null);
    }

    private List<RelatedMaterial> getRelatedMaterials(SQLiteDatabase db, String foreignKey) {
        ArrayList<RelatedMaterial> materials = new ArrayList<>();
        String[] projection = { RelatedMaterial.DB_COLUMN_HOW_RELATED_HREF,
                RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_URI, RelatedMaterial.DB_COLUMN_MEDIA_LOCATOR_CONTENT_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(RELATED_MATERIALS_TABLE, projection,
                    COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, COLUMN_INDEX);
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
        String[] projection = { AvailabilityPeriod.DB_COLUMN_VALID_FROM, AvailabilityPeriod.DB_COLUMN_VALID_TO, COLUMN_INDEX };
        ArrayList<AvailabilityPeriod> periods = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(AVAILABILITY_PERIOD_TABLE, projection,
                    COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, COLUMN_INDEX);
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
        String[] projection = { AvailabilityPeriod.Interval.DB_COLUMN_START_TIME, AvailabilityPeriod.Interval.DB_COLUMN_END_TIME, AvailabilityPeriod.Interval.DB_COLUMN_DAYS };
        Cursor cursor = null;
        try {
            cursor = db.query(AVAILABILITY_PERIOD_INTERVALS_TABLE, projection,
                    COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, COLUMN_INDEX);
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
        String[] projection = { ServiceInstance.DB_COLUMN_DELIVERY_PARAMS, ServiceInstance.DB_COLUMN_PRIORITY,
                COLUMN_INDEX, ServiceInstance.DB_COLUMN_DELIVERY_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICE_INSTANCES_TABLE, projection,
                    COLUMN_FOREIGN_KEY + "='" + uid + "'",
                    null, null, null, COLUMN_INDEX);
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
        db.delete(SERVICE_NAMES_TABLE, COLUMN_FOREIGN_KEY + " = '" + uid + "'", null);
        for (Map.Entry<String, String> entry : names.entrySet()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_FOREIGN_KEY, uid);
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
                    COLUMN_FOREIGN_KEY + "='" + uid + "'",
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
