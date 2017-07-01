package com.interview_assignment.vehicletracking;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by rajen on 1/7/17.
 */
public class DatabaseHelper extends SQLiteOpenHelper{

    private static final String DATABASE_NAME = "vehicle_tracking";

    private static final int DATABASE_VERSION = 1;

    private static final String LOCATION_TABLE = "location_info";

    private static DatabaseHelper sInstance;

    private static final String CREATE_TABLE = "CREATE TABLE " + LOCATION_TABLE + " (_id INTEGER PRIMARY KEY, locationTime TEXT, latitude TEXT, longitude TEXT," +
            "current_time_interval TEXT, next_time_interval TEXT)";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized DatabaseHelper getInstance(Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + LOCATION_TABLE);

            onCreate(sqLiteDatabase);
        }
    }

    public void addLocationRecord(String locationTime, String latitude, String longitude, String currentTimeInterval, String nextTimeInterval){
        ContentValues contentValues = new ContentValues();

        contentValues.put("locationTime", locationTime);
        contentValues.put("latitude", latitude);
        contentValues.put("longitude", longitude);
        contentValues.put("current_time_interval", currentTimeInterval);
        contentValues.put("next_time_interval", nextTimeInterval);

        SQLiteDatabase database = getWritableDatabase();

        database.beginTransaction();

        try {
            long recId = database.insertOrThrow(LOCATION_TABLE, null, contentValues);
            database.setTransactionSuccessful();

            Log.d("Vehicle", String.valueOf(recId));
        }catch (SQLException e){
            e.printStackTrace();
        }finally {
            database.endTransaction();
        }
    }
}
