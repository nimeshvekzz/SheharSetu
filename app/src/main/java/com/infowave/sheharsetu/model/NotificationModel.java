package com.infowave.sheharsetu.model;

public class NotificationModel {
    private String id;
    private String title;
    private String content;
    private String timestamp;
    private boolean isRead;
    private String type; // SUCCESS, INFO, WARNING

    public NotificationModel(String id, String title, String content, String timestamp, boolean isRead, String type) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
        this.type = type;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public String getType() { return type; }

    public void setRead(boolean read) { isRead = read; }
}
