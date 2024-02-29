package com.example.mailsbots.service;

import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

public class SendDocumentChatAction extends SendDocument {

    public SendDocumentChatAction() {
        super();
    }

    public SendDocumentChatAction setInputFile(InputFile inputFile) {
        super.setDocument(inputFile);
        return this;
    }
}
