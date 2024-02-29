package com.example.mailsbots.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

@Service
public class PicService {

    public String saveFileFromUrl(String fileUrl) {
        try {
            // Определите имя файла на основе URL
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

            // Задайте директорию для сохранения файлов
            String directoryPath = "C:/Users/ChessMan/Desktop/pic";

            // Полный путь к файлу в файловой системе
            String filePath = directoryPath + File.separator + fileName;

            // Откройте InputStream с URL и FileOutputStream для сохранения файла
            try (InputStream in = new URL(fileUrl).openStream();
                 FileOutputStream out = new FileOutputStream(filePath)) {
                // Скопируйте содержимое файла
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return filePath;
        } catch (IOException e) {
            e.printStackTrace();
            // Обработайте исключение или верните null в случае ошибки
            return null;
        }
    }
}
