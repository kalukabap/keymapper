package com.genymobile.scrcpy;

public class Pointer {
    private int id;
    private Point point;
    private float pressure;
    private boolean up;

    public Pointer() {
        pressure = 1.0f;
        up = true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Point getPoint() { return point; }
    public void setPoint(Point point) { this.point = point; }
    public float getPressure() { return pressure; }
    public void setPressure(float pressure) { this.pressure = pressure; }
    public boolean isUp() { return up; }
    public void setUp(boolean up) { this.up = up; }
}
