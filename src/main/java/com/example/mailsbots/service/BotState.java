package com.example.mailsbots.service;

public enum BotState {
    DEFAULT,
    WAITING_FOR_CREATE,
    WAITING_FOR_TIME,
    WAITING_FOR_Image,
    WAITING_FOR_TITLE,
    WAITING_FOR_DOC,
    WAITING_FOR_ChannelText,
}
