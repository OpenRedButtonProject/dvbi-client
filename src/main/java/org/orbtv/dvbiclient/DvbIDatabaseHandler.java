package org.orbtv.dvbiclient;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DvbIDatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = DvbIDatabaseHandler.class.getSimpleName();
    private static final int DB_VERSION = 16;
    private static final String DB_NAME = "dvbi_db";
    private static final String FOREIGN_KEY_PREFIX_SERVICE = "service_";
    private static final String FOREIGN_KEY_PREFIX_INSTANCE = "instance_";
    private static final String COLUMN_ID = "id";

    private static final String SERVICE_LISTS_TABLE = "service_lists";
    private static final String SERVICE_LISTS_COLUMN_NAME = "name";
    private static final String SERVICE_LISTS_COLUMN_PROVIDER = "provider";

    private static final String SERVICES_TABLE = "services";
    private static final String SERVICES_COLUMN_SERVICE_LIST_ID = "service_list_id";
    private static final String SERVICES_COLUMN_UNIQUE_IDENTIFIER = "unique_identifier";
    private static final String SERVICES_COLUMN_LCN = "lcn";
    private static final String SERVICES_COLUMN_PROVIDER = "provider";
    private static final String SERVICES_COLUMN_ADDITIONAL_PARAMS = "additional_params";
    private static final String SERVICES_COLUMN_SERVICE_TYPE = "service_type";

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
    private static final String AVAILABILITY_PERIOD_COLUMN_START_TIME = "start_time";
    private static final String AVAILABILITY_PERIOD_COLUMN_END_TIME = "end_time";

    private static final String SERVICE_NAMES_TABLE = "service_names";
    private static final String SERVICE_NAMES_COLUMN_SERVICE_UID = "service_uid";
    private static final String SERVICE_NAMES_COLUMN_NAME = "name";
    private static final String SERVICE_NAMES_COLUMN_COUNTRY = "country";

    public DvbIDatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SERVICE_LISTS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICE_LISTS_COLUMN_NAME + " TEXT, "
                + SERVICE_LISTS_COLUMN_PROVIDER + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICES_COLUMN_SERVICE_LIST_ID + " INTEGER NOT NULL, "
                + SERVICES_COLUMN_UNIQUE_IDENTIFIER + " TEXT NOT NULL UNIQUE, "
                + SERVICES_COLUMN_LCN + " TEXT,"
                + SERVICES_COLUMN_PROVIDER + " TEXT,"
                + SERVICES_COLUMN_SERVICE_TYPE + " TEXT,"
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
               + AVAILABILITY_PERIOD_COLUMN_START_TIME + " TEXT NOT NULL, "
               + AVAILABILITY_PERIOD_COLUMN_END_TIME + " TEXT NOT NULL)"
       );

        // TODO: remove block
        ContentValues values = new ContentValues();
        values.put(SERVICE_LISTS_COLUMN_NAME, "my service list");
        values.put(SERVICE_LISTS_COLUMN_PROVIDER, "my provider");
        db.insert(SERVICE_LISTS_TABLE, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Log.i(TAG, "Dropping database tables...");
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_INSTANCES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_LISTS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + RELATED_MATERIALS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SERVICE_NAMES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + AVAILABILITY_PERIOD_TABLE);
        // TODO: delete dvbi services from the android channel database
        onCreate(db);
    }

    public synchronized List<DvbIService> getServices() {
        ArrayList<DvbIService> ret = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = { SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER,
                SERVICES_COLUMN_LCN, SERVICES_COLUMN_ADDITIONAL_PARAMS, SERVICES_COLUMN_SERVICE_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection, null, null, null, null, null);
            if (cursor != null)
            {
                while (cursor.moveToNext())
                {
                    Triplet triplet = null;
                    try {
                        String dvbUri = new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet");
                        if (dvbUri != null) {
                            triplet = new Triplet(dvbUri);
                        }
                    }
                    catch (JSONException e) { }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    ret.add(new DvbIService(getServiceNamesForUID(db, cursor.getString(1)), cursor.getString(0),
                            cursor.getString(1), cursor.getString(4), cursor.getString(2), triplet,
                            getServiceInstancesForUID(db, cursor.getString(1)),
                            getRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + cursor.getString(1))));
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

    public synchronized DvbIService getServiceForUID(String uid) {
        DvbIService service = null;
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = { SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER,
                SERVICES_COLUMN_LCN, SERVICES_COLUMN_ADDITIONAL_PARAMS, SERVICES_COLUMN_SERVICE_TYPE };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection,
                    SERVICES_COLUMN_UNIQUE_IDENTIFIER + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null && cursor.moveToNext())
            {
                Triplet triplet = null;
                try {
                    String dvbUri = new JSONObject(new String(cursor.getBlob(3))).getString("hbbtv-i:DVBTriplet");
                    if (dvbUri != null) {
                        triplet = new Triplet(dvbUri);
                    }
                }
                catch (Exception e) { }
                service = new DvbIService(getServiceNamesForUID(db, cursor.getString(1)), cursor.getString(0),
                        cursor.getString(1), cursor.getString(4), cursor.getString(2), triplet,
                        getServiceInstancesForUID(db, cursor.getString(1)),
                        getRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + cursor.getString(1)));
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

    public synchronized void updateServices(List<DvbIService> services) {
        SQLiteDatabase db = getWritableDatabase();
        String uids = "";
        for (DvbIService service : services) {
            String uid = service.getUniqueIdentifier();
            if (!uids.isEmpty()) {
                uids += ",'" + uid + "'";
            }
            else {
                uids += "'" + uid + "'";
            }
            updateService(db, service);
            for (int i = 0; i < service.getInstances().size(); ++i) {
                uids += ",'" + uid + "_" + i + "'";
            }
        }
        if (!uids.isEmpty()) {
            Log.i(TAG, "Deleting service and instances with UIDs not in " + uids);
            db.delete(SERVICES_TABLE, SERVICES_COLUMN_UNIQUE_IDENTIFIER + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_INSTANCES_TABLE, SERVICE_INSTANCES_COLUMN_SERVICE_UID + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_NAMES_TABLE, SERVICE_NAMES_COLUMN_SERVICE_UID + " NOT IN (" + uids + ")", null);
            // TODO: delete related materials for non-existent services/instances
        }
    }

    private void updateService(SQLiteDatabase db, DvbIService service) {
        String uid = service.getUniqueIdentifier();
        ContentValues values = new ContentValues();
        JSONObject params = new JSONObject();
        values.put(SERVICES_COLUMN_SERVICE_LIST_ID, 1);
        values.put(SERVICES_COLUMN_PROVIDER, service.getProviderName());
        values.put(SERVICES_COLUMN_UNIQUE_IDENTIFIER, uid);
        values.put(SERVICES_COLUMN_LCN, service.getLCNNumber());
        values.put(SERVICES_COLUMN_SERVICE_TYPE, service.getServiceType());
        if (service.getTriplet() != null) {
            try {
                params.put("hbbtv-i:DVBTriplet", service.getTriplet());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        values.put(SERVICES_COLUMN_ADDITIONAL_PARAMS, params.toString().getBytes(StandardCharsets.UTF_8));

        if (db.update(SERVICES_TABLE, values, SERVICES_COLUMN_UNIQUE_IDENTIFIER + "='" + uid + "'", null) == 0) {
            db.insert(SERVICES_TABLE, null, values);
            Log.d(TAG, "Adding service " + uid);
        }
        else {
            Log.d(TAG, "Updating service " + uid);
        }

        updateRelatedMaterials(db, FOREIGN_KEY_PREFIX_SERVICE + uid, service.getRelatedMaterials());
        updateServiceNames(db, uid, service.getServiceNames());

        List<DvbIServiceInstance> instances = service.getInstances();
        for (int i = 0; i < instances.size(); ++i) {
            DvbIServiceInstance instance = instances.get(i);
            params = new JSONObject();
            values = new ContentValues();
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

            InstanceAvailabilityPeriod availabilityPeriod = instance.getAvailabilityPeriod();
            if (availabilityPeriod != null) {
                updateAvailabilityPeriod(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + i, availabilityPeriod);
            }
        }
        // in case there are less services than before, delete those that have
        // higher index than the size of the current list
        db.delete(SERVICE_INSTANCES_TABLE, SERVICE_INSTANCES_COLUMN_SERVICE_UID
                + "='" + uid + "' AND " + SERVICE_INSTANCES_COLUMN_INDEX + ">="
                + instances.size(), null);
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

    private void updateAvailabilityPeriod(SQLiteDatabase db, String foreignKey, InstanceAvailabilityPeriod availabilityPeriod) {
        List<String> startTimes = availabilityPeriod.getStartTimes();
        List<String> endTimes = availabilityPeriod.getEndTimes();

        for (int i = 0; i < startTimes.size(); i++) {
            ContentValues values = new ContentValues();
            values.put(AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY, foreignKey);
            values.put(AVAILABILITY_PERIOD_COLUMN_START_TIME, startTimes.get(i));
            values.put(AVAILABILITY_PERIOD_COLUMN_END_TIME, endTimes.get(i));
            values.put(AVAILABILITY_PERIOD_COLUMN_INDEX, i);
            if (db.update(AVAILABILITY_PERIOD_TABLE, values, AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY
                    + "='" + foreignKey + "' AND " + AVAILABILITY_PERIOD_COLUMN_INDEX
                    + "=" + i, null) == 0) {
                db.insert(AVAILABILITY_PERIOD_TABLE, null, values);
            }
        }
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
                while (cursor.moveToNext()) {
                    materials.add (new RelatedMaterial(cursor.getString(0),
                            cursor.getString(1), cursor.getString(2)));
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

    private InstanceAvailabilityPeriod getAvailabilityPeriod(SQLiteDatabase db, String foreignKey) {
        List<String> startTimes = new ArrayList<>();
        List<String> endTimes = new ArrayList<>();
        String[] projection = { AVAILABILITY_PERIOD_COLUMN_START_TIME, AVAILABILITY_PERIOD_COLUMN_END_TIME };
        Cursor cursor = null;
        try {
            cursor = db.query(AVAILABILITY_PERIOD_TABLE, projection,
                    AVAILABILITY_PERIOD_COLUMN_FOREIGN_KEY + "='" + foreignKey + "'",
                    null, null, null, AVAILABILITY_PERIOD_COLUMN_INDEX);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    startTimes.add(cursor.getString(0));
                    endTimes.add(cursor.getString(1));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new InstanceAvailabilityPeriod(startTimes, endTimes);
    }

    private List<DvbIServiceInstance> getServiceInstancesForUID(SQLiteDatabase db, String uid) {
        ArrayList<DvbIServiceInstance> instances = new ArrayList<>();
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
                while (cursor.moveToNext()) {
                    try {
                        instances.add(new DvbIServiceInstance(getServiceNamesForUID(db, uid + "_" + instances.size()), cursor.getInt(1),
                                cursor.getString(3), new JSONObject(new String(cursor.getBlob(0))),
                                getRelatedMaterials(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + cursor.getInt(2)),
                                getAvailabilityPeriod(db, FOREIGN_KEY_PREFIX_INSTANCE + uid + "_" + cursor.getInt(2))));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
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
}
