package org.orbtv.dvbiclient;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DvbIDatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = DvbIDatabaseHandler.class.getSimpleName();
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "dvbi_db";
    private static final String COLUMN_ID = "id";

    private static final String SERVICE_LISTS_TABLE = "service_lists";
    private static final String SERVICE_LISTS_COLUMN_NAME = "name";
    private static final String SERVICE_LISTS_COLUMN_PROVIDER = "provider";

    private static final String SERVICES_TABLE = "services";
    private static final String SERVICES_COLUMN_SERVICE_LIST_ID = "service_list_id";
    private static final String SERVICES_COLUMN_UNIQUE_IDENTIFIER = "unique_identifier";
    private static final String SERVICES_COLUMN_NAME = "name";
    private static final String SERVICES_COLUMN_PROVIDER = "provider";

    private static final String SERVICE_INSTANCES_TABLE = "service_instances";
    private static final String SERVICE_INSTANCES_COLUMN_SERVICE_UID = "service_uid";
    private static final String SERVICE_INSTANCES_COLUMN_URI = "uri";
    private static final String SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS = "delivery_parameters";

    public DvbIDatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        //onUpgrade(getWritableDatabase(), 0, 0);
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
                + SERVICES_COLUMN_NAME + " TEXT, "
                + SERVICES_COLUMN_PROVIDER + " TEXT)"
        );
        db.execSQL("CREATE TABLE " + SERVICE_INSTANCES_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SERVICE_INSTANCES_COLUMN_SERVICE_UID + " TEXT NOT NULL, "
                + SERVICE_INSTANCES_COLUMN_URI + " TEXT, "
                + SERVICE_INSTANCES_COLUMN_DELIVERY_PARAMS + " BLOB)"
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
        onCreate(db);
    }

    public synchronized List<DvbIServiceInstance> getServiceInstancesForUID(String uid) {
        ArrayList<DvbIServiceInstance> instances = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = { SERVICE_INSTANCES_COLUMN_URI };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICE_INSTANCES_TABLE, projection,
                    SERVICE_INSTANCES_COLUMN_SERVICE_UID + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null)
            {
                while (cursor.moveToNext()) {
                    instances.add(new DvbIServiceInstance("lala", "lalala", 1,
                            cursor.getString(0), null, null));
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

    public synchronized List<DvbIService> getServices() {
        ArrayList<DvbIService> ret = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = { SERVICES_COLUMN_NAME, SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection, null, null, null, null, null);
            if (cursor != null)
            {
                while (cursor.moveToNext())
                {
                    ret.add(new DvbIService(cursor.getString(0), cursor.getString(1),
                            cursor.getString(2), null, getServiceInstancesForUID(cursor.getString(2))));
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
        String[] projection = { SERVICES_COLUMN_NAME, SERVICES_COLUMN_PROVIDER, SERVICES_COLUMN_UNIQUE_IDENTIFIER };
        Cursor cursor = null;
        try
        {
            cursor = db.query(SERVICES_TABLE, projection,
                    SERVICES_COLUMN_UNIQUE_IDENTIFIER + "='" + uid + "'",
                    null, null, null, null);
            if (cursor != null && cursor.moveToNext())
            {
                service = new DvbIService(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), null, getServiceInstancesForUID(cursor.getString(2)));
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
            String[] projection = {COLUMN_ID};
            long id = -1;
            Cursor cursor = null;
            try {
                cursor = db.query(SERVICES_TABLE, projection, SERVICES_COLUMN_UNIQUE_IDENTIFIER + "=?",
                        new String[]{uid}, null, null, null);
                if (cursor != null && cursor.moveToNext()) {
                    id = cursor.getLong(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            ContentValues values = new ContentValues();
            values.put(SERVICES_COLUMN_SERVICE_LIST_ID, 1);
            values.put(SERVICES_COLUMN_NAME, "My DVB-I service");
            values.put(SERVICES_COLUMN_PROVIDER, service.getProviderName());
            values.put(SERVICES_COLUMN_UNIQUE_IDENTIFIER, uid);
            if (id == -1) {
                db.insert(SERVICES_TABLE, null, values);
                for (DvbIServiceInstance instance : service.getInstances()) {
                    values = new ContentValues();
                    values.put(SERVICE_INSTANCES_COLUMN_SERVICE_UID, uid);
                    db.insert(SERVICE_INSTANCES_TABLE, null, values);
                }
                Log.d(TAG, "Adding service " + uid);
            } else {
                db.update(SERVICES_TABLE, values, COLUMN_ID + "=" + id, null);
                for (DvbIServiceInstance instance : service.getInstances()) {
                    // TODO: update service instances by id
                    values = new ContentValues();
                    values.put(SERVICE_INSTANCES_COLUMN_URI, instance.getUri());
                    db.update(SERVICE_INSTANCES_TABLE, values,
                            SERVICE_INSTANCES_COLUMN_SERVICE_UID + "='" + uid + "'", null);
                }
                // TODO: delete non-existent instances
                Log.d(TAG, "Updating service " + uid);
            }
        }
        if (!uids.isEmpty()) {
            Log.i(TAG, "Deleting service and instances with UIDs not in " + uids);
            db.delete(SERVICES_TABLE, SERVICES_COLUMN_UNIQUE_IDENTIFIER + " NOT IN (" + uids + ")", null);
            db.delete(SERVICE_INSTANCES_TABLE, SERVICE_INSTANCES_COLUMN_SERVICE_UID + " NOT IN (" + uids + ")", null);
        }
    }
}
