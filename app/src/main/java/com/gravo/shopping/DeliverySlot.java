package com.gravo.shopping;
public class DeliverySlot {
    private int startHour;
    private int endHour;
    private int dayOffset; // 0 = Today, 1 = Tomorrow
    private String timeText;

    public DeliverySlot() {} // Firestore/Gson ke liye required

    public DeliverySlot(int startHour, int endHour, int dayOffset, String timeText) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.dayOffset = dayOffset;
        this.timeText = timeText;
    }

    public int getStartHour() { return startHour; }
    public int getEndHour() { return endHour; }
    public int getDayOffset() { return dayOffset; }
    public String getTimeText() { return timeText; }
}