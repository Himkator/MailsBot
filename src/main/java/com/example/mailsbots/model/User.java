package com.example.mailsbots.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.sql.Timestamp;

@Entity(name="User")
public class User {
    @Id
    private Long ChatId;
    private String name;
    private Timestamp timestamp;

    public Long getChatId() {
        return ChatId;
    }

    public void setChatId(Long chatId) {
        ChatId = chatId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}
