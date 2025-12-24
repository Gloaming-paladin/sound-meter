// file: app/src/main/java/com/bodekjan/soundmeter/model/NoiseData.java
package com.bodekjan.soundmeter;

public class NoiseData {
    private long id;
    private double dbValue; // 数据库中为 REAL，使用 double 更安全
    private double latitude;
    private double longitude;
    private long timestamp;
    private String path; // 统一为 path，与数据库列 COLUMN_RECORDING_PATH 对应

    // 空构造函数（用于反序列化或 ORM）
    public NoiseData() {}

    // 主要构造函数（用于创建新数据）
    public NoiseData(double dbValue, double latitude, double longitude, long timestamp, String path) {
        this.dbValue = dbValue;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.path = path;
    }

    // getter 和 setter 方法
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public double getDbValue() { return dbValue; }
    public void setDbValue(double dbValue) { this.dbValue = dbValue; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    // toString 方法（便于调试）
    @Override
    public String toString() {
        return "NoiseData{" +
                "id=" + id +
                ", dbValue=" + dbValue +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", timestamp=" + timestamp +
                ", path='" + path + '\'' +
                '}';
    }
}
