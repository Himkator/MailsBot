package com.example.mailsbots.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity(name="mail")
@Getter
@Setter
public class Mails {
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Id
    private Long id;
    private Long chatId;
    @Column(length = 25500)
    private String title;
    @Column(length = 255000)
    private String body;
    private Timestamp date;
    private String photo;
    private String channel;
    private String doc;
    private String DocPath;
    private String picPath;
    private String bodyAfter;






    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }




    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }


    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public String getDocPath() {
        return DocPath;
    }

    public void setDocPath(String docPath) {
        DocPath = docPath;
    }

    public String getPicPath() {
        return picPath;
    }

    public void setPicPath(String picPath) {
        this.picPath = picPath;
    }
}
