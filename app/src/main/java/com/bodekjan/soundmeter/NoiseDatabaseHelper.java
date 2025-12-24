package com.bodekjan.soundmeter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单 SQLite helper，包名与文件路径一致，使用 com.bodekjan.soundmeter.NoiseData（带字段和构造器）.
 */
public class NoiseDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "noisedb.db";
    private static final int DATABASE_VERSION = 1;

    // 表名和列名
    public static final String TABLE_NOISE = "noise_data";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_DB_VALUE = "db_value";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_RECORDING_PATH = "recording_path";

    // 创建表的SQL语句
    private static final String CREATE_TABLE_NOISE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NOISE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_DB_VALUE + " REAL NOT NULL, " +
                    COLUMN_LATITUDE + " REAL, " +
                    COLUMN_LONGITUDE + " REAL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_RECORDING_PATH + " TEXT" +
                    ")";

    public NoiseDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_NOISE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOISE);
        onCreate(db);
    }

    // 插入噪音数据（使用 NoiseData 的 public 字段）
// insertNoiseData
    public long insertNoiseData(NoiseData noiseData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DB_VALUE, noiseData.getDbValue());
        values.put(COLUMN_LATITUDE, noiseData.getLatitude());
        values.put(COLUMN_LONGITUDE, noiseData.getLongitude());
        values.put(COLUMN_TIMESTAMP, noiseData.getTimestamp());
        values.put(COLUMN_RECORDING_PATH, noiseData.getPath());

        long id = db.insert(TABLE_NOISE, null, values);
        db.close();
        return id;
    }


    public void clearAllNoiseData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOISE, null, null);
        db.close();
    }


    // 获取所有噪音数据（返回使用现有 NoiseData 构造器）
    public List<NoiseData> getAllNoiseData() {
        List<NoiseData> noiseDataList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_NOISE + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        double dbValue = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DB_VALUE));
                        double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE));
                        double lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE));
                        long ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                        String path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDING_PATH));

                        NoiseData noiseData = new NoiseData(dbValue, lat, lng, ts, path);
                        noiseData.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                        noiseDataList.add(noiseData);
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        db.close();
        return noiseDataList;
    }


}
