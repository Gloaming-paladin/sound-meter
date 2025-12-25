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

    // 用户表
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_AVATAR = "avatar";

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

    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
                    COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    COLUMN_PASSWORD + " TEXT NOT NULL, " +
                    COLUMN_AVATAR + " TEXT" +
                    ")";

    public NoiseDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_NOISE);
        db.execSQL(CREATE_TABLE_USERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOISE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
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

    // 添加用户
    public boolean addUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USERNAME, username);
        values.put(COLUMN_PASSWORD, password); // 在实际应用中，密码应该被加密存储

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result != -1;
    }

    // 检查用户是否存在（登录）
    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_USER_ID};
        String selection = COLUMN_USERNAME + "=? AND " + COLUMN_PASSWORD + "=?";
        String[] selectionArgs = {username, password};

        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();

        return count > 0;
    }

    // 检查用户名是否已存在（注册）
    public boolean checkUserExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_USER_ID};
        String selection = COLUMN_USERNAME + "=?";
        String[] selectionArgs = {username};

        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();

        return count > 0;
    }

    // 更新用户头像
    public int updateUserAvatar(String username, String avatarPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AVATAR, avatarPath);
        int result = db.update(TABLE_USERS, values, COLUMN_USERNAME + " = ?", new String[]{username});
        db.close();
        return result;
    }

    // 获取用户头像
    public String getUserAvatar(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_AVATAR};
        String selection = COLUMN_USERNAME + " = ?";
        String[] selectionArgs = {username};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);

        String avatarPath = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    avatarPath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AVATAR));
                }
            } finally {
                cursor.close();
            }
        }
        db.close();
        return avatarPath;
    }

    // 获取所有噪音数据
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

    // 清除所有噪音数据
    public void clearAllNoiseData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOISE, null, null);
        db.close();
    }

    // 获取用户信息
    public Cursor getUser(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_USER_ID, COLUMN_USERNAME, COLUMN_AVATAR};
        String selection = COLUMN_USERNAME + "=?";
        String[] selectionArgs = {username};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }


}
