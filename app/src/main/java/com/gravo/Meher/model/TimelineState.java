package com.gravo.Meher.model;

public class TimelineState {
    private final int stage;
    private final boolean visible;
    private final String timelineSubtitle;
    private final String placedTime;
    private final String confirmedTime;
    private final String packedTime;
    private final String outForDeliveryTime;
    private final String deliveredTime;

    public TimelineState(int stage, boolean visible, String timelineSubtitle,
                         String placedTime, String confirmedTime, String packedTime,
                         String outForDeliveryTime, String deliveredTime) {
        this.stage = stage;
        this.visible = visible;
        this.timelineSubtitle = timelineSubtitle;
        this.placedTime = placedTime;
        this.confirmedTime = confirmedTime;
        this.packedTime = packedTime;
        this.outForDeliveryTime = outForDeliveryTime;
        this.deliveredTime = deliveredTime;
    }

    public int getStage() { return stage; }
    public boolean isVisible() { return visible; }
    public String getTimelineSubtitle() { return timelineSubtitle; }
    public String getPlacedTime() { return placedTime; }
    public String getConfirmedTime() { return confirmedTime; }
    public String getPackedTime() { return packedTime; }
    public String getOutForDeliveryTime() { return outForDeliveryTime; }
    public String getDeliveredTime() { return deliveredTime; }
}
