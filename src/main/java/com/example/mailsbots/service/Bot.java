package com.example.mailsbots.service;

import com.example.mailsbots.config.BotConfig;
import com.example.mailsbots.model.*;
import com.example.mailsbots.model.User;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;


import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.springframework.util.ResourceUtils.getFile;


@Service
public class Bot extends TelegramLongPollingBot {
    //Авторизация репозиториев
    @Autowired
    private ChatmemberRepository chatmemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MailsRepository mailsRepository;
    @Autowired
    BotConfig botConfig;
    @Autowired
    private FileService fileService;
    @Autowired
    private PicService picService;
    @Autowired
    private Mail_NowRepository mail_nowRepository;
    //текст рассылки
    private String body;
    private String bodyChannel;
    //путь до документа
    private String docContent;
    //путь до картины
    private String picContemt;
    //время отправки
    private String time;
    //название рассылки
    private String title;
    //создал элемент класса чтобы во время функции лист получит типа кто это отправил и проверят
    private Mails mails;
    //первоначалные критерии
    public String pic="No";
    public String channel="No";
    public String doc="No";
    String channelAfter="No";
//    private String FileId;
//    public String getFileId() {
//        return FileId;
//    }
//
//    public void setFileId(String fileId) {
//        FileId = fileId;
//    }

    //массив который сохраняет коллбаки у лист оф мейлс
    public List<String> mail_callback=new ArrayList<>();
    //список рассылок
    public List<Mails> mailsList=new ArrayList<>();
    //список состояний сообщения
    private Map<Long, BotState> userStates = new HashMap<>();
    //text for function help
    final static String Help_text="I am your personal AccontanatBot, I can record your financial expenses and income.\n" +
            "If you want record information about expenses, just write or take from menu '/spend'\n" +
            "or if you want record information abot income, write '/earn'.\n" +
            "Also you can watch your history with '/history',\n or watch how many you spend or earn with '/sum'.\n" +
            "Or you can delete all your information with /delete.\nI hope I will be benefit for you.";
    //Конструктор где собирается бот
    public Bot(BotConfig botConfig){
        this.botConfig=botConfig;
        //Добавление меню в чате сперва листом вклюсил все BotCommand это тип данных для команд
        List<BotCommand> listofCommands=new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "get a welcome message"));
        listofCommands.add(new BotCommand("/create", "create mail for send"));
        listofCommands.add(new BotCommand("/list_of_mails", "ready list for mail"));
        listofCommands.add(new BotCommand("/delete", "delete all mails"));
        listofCommands.add(new BotCommand("/help", "info how to use bot"));
        try{
            //Меняет меню, чтобы туда попали вещи из списка
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //получаем имя бота
    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }
    //получаем его токен
    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }
    //проверят есть ли сообщение
    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        // проверяет еслть сообщение и в сообщение есть текст ведь есть еще CallBack
        if(update.hasMessage() && update.getMessage().hasText()){
            //получил сообщение
            String msgText=update.getMessage().getText();
            //получил айди чата
            long ChatId=update.getMessage().getChatId();
            //проверка всех сообщений
            if(msgText.contains("/start")) startCommand(ChatId, update.getMessage().getChat().getFirstName());
            else if (msgText.equals("/help")) sendMessage(ChatId, Help_text);
            else if (msgText.equals("/create")) {
                create(ChatId);
                channel="No";
                pic="No";
                doc="No";
            }
            else if(msgText.equals("/list_of_mails")){list_mail(ChatId);}
            else if (msgText.equals("/delete")) {
                delete_mail(ChatId);
            } else{
                try {
                    //проверка состояние сообщение
                    processUserInput(ChatId, msgText);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }

            }
        }
        //если не сообщение а колбак
        else if(update.hasCallbackQuery()){
            //берем его колбакдата для проверки
            String callBack=update.getCallbackQuery().getData();
            //получаем айди сообщение
            long messageId=update.getCallbackQuery().getMessage().getMessageId();
            //получение айди ползователя
            long chatId=update.getCallbackQuery().getMessage().getChatId();
            String text_delete="";
            //выбор критерии
            if(callBack.equals("Pic")){
                String text="Добавить фото на рассылку? На рассылку можно добавить только одно фото и\nтакже если на рассылке есть фото нельзя добавить документ";
                var Pic_Yes=new InlineKeyboardButton();
                Pic_Yes.setText("Да");
                Pic_Yes.setCallbackData("Pic_Yes");

                var Pic_No=new InlineKeyboardButton();
                Pic_No.setText("Нет");
                Pic_No.setCallbackData("Pic_No");
                Yes_NO(chatId, messageId, text, Pic_Yes, Pic_No);
            }else if(callBack.equals("Pic2")){
                String text="Добавить фото на рассылку? На рассылку можно добавить только одно фото и\nтакже если на рассылке есть фото нельзя добавить документ";
                var Pic_Yes=new InlineKeyboardButton();
                Pic_Yes.setText("Да");
                Pic_Yes.setCallbackData("Pic_Yes2");

                var Pic_No=new InlineKeyboardButton();
                Pic_No.setText("Нет");
                Pic_No.setCallbackData("Pic_No");
                Yes_NO(chatId, messageId, text, Pic_Yes, Pic_No);
            }
            else if (callBack.equals("Channel")) {
                String text="Добавить проверку на подпиксу на рассылку? \nЕсли вы выберите это то отправка документа будет \nпосле проверки и вот почему временно будет это выключена функция";
                var Channel_Yes=new InlineKeyboardButton();
                Channel_Yes.setText("Да");
                Channel_Yes.setCallbackData("Channel_Yes");

                var Channel_No=new InlineKeyboardButton();
                Channel_No.setText("Нет");
                Channel_No.setCallbackData("Channel_No");
                Yes_NO(chatId, messageId, text, Channel_Yes, Channel_No);
            } else if (callBack.equals("Doc")) {
                String text="Добавить документ или файл на рассылку?\n В рассылку можно добавить только один документ и\nтакже если на рассылке есть документ нельзя добавить фото";
                var Doc_Yes=new InlineKeyboardButton();
                Doc_Yes.setText("Да");
                Doc_Yes.setCallbackData("Doc_Yes");

                var Doc_No=new InlineKeyboardButton();
                Doc_No.setText("Нет");
                Doc_No.setCallbackData("Doc_No");
                Yes_NO(chatId, messageId, text, Doc_Yes, Doc_No);
                //если критерии все принято
            }else if (callBack.equals("Doc2")) {
                String text="Добавить документ или файл на рассылку?\n В рассылку можно добавить только один документ и\nтакже если на рассылке есть документ нельзя добавить фото";
                var Doc_Yes=new InlineKeyboardButton();
                Doc_Yes.setText("Да");
                Doc_Yes.setCallbackData("Doc_Yes2");

                var Doc_No=new InlineKeyboardButton();
                Doc_No.setText("Нет");
                Doc_No.setCallbackData("Doc_No");
                Yes_NO(chatId, messageId, text, Doc_Yes, Doc_No);
                //если критерии все принято
            } else if (callBack.equals("Ideal")) {
                String text="Давайте сперва придумаем названия рассылки чтобы сохранить под таким названием";
                setUserState(chatId, BotState.WAITING_FOR_TITLE);
                EditMessageText messageText=new EditMessageText();
                messageText.setChatId(String.valueOf(chatId));
                messageText.setText(text);
                messageText.setMessageId((int) messageId);
                try{
                    execute(messageText);
                }catch(TelegramApiException e){
                    System.out.println("We have problem "+e.getMessage());
                }
            } else if (callBack.equals("IdealChannel")) {
                String text="Дайте текст после проверки";
                setUserState(chatId, BotState.WAITING_FOR_ChannelText);
                EditMessageText messageText=new EditMessageText();
                messageText.setChatId(String.valueOf(chatId));
                messageText.setText(text);
                messageText.setMessageId((int) messageId);
                channelAfter="Yes";
                try{
                    execute(messageText);
                }catch(TelegramApiException e){
                    System.out.println("We have problem "+e.getMessage());
                }
            } else if (callBack.equals("Pic_Yes")) {
                pic="Yes";
                if(doc.equals("Yes")){
                    doc="No";
                }
                if(channel.equals("Yes")) channel="No";
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
            }else if (callBack.equals("Pic_Yes2")) {
                pic="Yes";
                if(doc.equals("Yes")){
                    doc="No";
                }
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
            } else if(callBack.equals("Pic_No")){
                pic="No";
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
            } else if (callBack.equals("Channel_Yes")) {
                if(doc.equals("Yes")) doc="No";
                if(pic.equals("Yes")) pic="No";
                channel="Yes";
                channelAfter="Yes";
                create_edit(chatId, messageId);
            } else if (callBack.equals("Channel_No")) {
                channel="No";
                channelAfter="No";
                create_edit(chatId, messageId);
            } else if (callBack.equals("Doc_Yes")) {
                doc="Yes";
                if(pic.equals("Yes")){
                    pic="No";
                }
                if(channel.equals("Yes")) channel="No";
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
            }else if (callBack.equals("Doc_Yes2")) {
                doc="Yes";
                if(pic.equals("Yes")){
                    pic="No";
                }
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
            } else if (callBack.equals("Doc_No")) {
                doc="No";
                if(channelAfter.equals("Yes"))
                    create_edit_channel(chatId, messageId);
                else
                    create_edit(chatId, messageId);
                //удаление данных
            } else if(callBack.equals("Yes_Delete")){
                text_delete="All your data was cleaned(deleted)!";
                var mails=mailsRepository.findAll();
                for(Mails user:mails){
                    if(user.getChatId()==chatId){
                        mailsRepository.deleteById(user.getId());
                    }
                }

            }
            else if(callBack.equals("No_Delete")){
                text_delete="OK, your date isn't deleted";
            }
            EditMessageText messageText=new EditMessageText();
            messageText.setChatId(String.valueOf(chatId));
            messageText.setText(text_delete);
            messageText.setMessageId((int) messageId);
            try{
                execute(messageText);
            }catch(TelegramApiException e){
                System.out.println("We have problem "+e.getMessage());
            }
            //проверка из массива рассылок колбака
            for (int i = 0; i < mail_callback.size(); i++) {
                if(callBack.equals(mail_callback.get(i))){
                    Mails mail=mailsList.get(i);
                    String text=" ";
                    text+="Название: "+mail.getTitle()+"\n \n";
                    text+="Фото: "+mail.getPhoto()+"\n";
                    text+="Документ(Файл): "+mail.getDoc()+"\n";
                    text+="Проверка на подписку: "+mail.getChannel()+"\n \n";
                    text+="Текст: \n"+mail.getBody()+"\n \n";
                    text+="Время: "+mail.getDate()+"\n";
                    if(mail.getChannel().equals("Yes"))
                        text+="Текст после проверки на подписку:\n"+mail.getBodyAfter();

                    EditMessageText messageTextMail=new EditMessageText();
                    messageTextMail.setChatId(String.valueOf(chatId));
                    messageTextMail.setText(text);
                    messageTextMail.setMessageId((int) messageId);
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Посмотреть как это выглядит у пользователя");
                    watch.setCallbackData("watch");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    messageTextMail.setReplyMarkup(inKey);
                    setMails(mail);
                    try{
                        execute(messageTextMail);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                }
            }
            //вернутся назад к списку
            if(callBack.equals("back")){back(chatId, messageId);}
            else if (callBack.equals("Exit")) {EditMessageText messageTextMail=new EditMessageText();
                messageTextMail.setChatId(String.valueOf(chatId));
                messageTextMail.setText("Хорошо, вы можете создать еще рассылки с помощью /create");
                messageTextMail.setMessageId((int) messageId);
                try{
                    execute(messageTextMail);
                }catch(TelegramApiException e){
                    System.out.println("We have problem"+e.getMessage());
                }
                //просмотр как это выглядит
            } else if (callBack.equals("watch")) {
                Mails mailWatch=getMails();
                //проверка всех критерии
                if(mailWatch.getDoc().equals("Yes") && mailWatch.getChannel().equals("No")){
                    Path path = Paths.get(mailWatch.getDocPath());
                    // Создание объекта SendDocument
                    SendDocument sendDocument = new SendDocument();
                    sendDocument.setChatId(String.valueOf(chatId));  // chatId пользователя, которому вы хотите отправить документ
                    sendDocument.setDocument(new InputFile(path.toFile()));
                    sendDocument.setCaption(mailWatch.getBody());
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    sendDocument.setReplyMarkup(inKey);
                    try{
                        execute(sendDocument);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                }
                else if (mailWatch.getDoc().equals("Yes") &&  mailWatch.getChannel().equals("Yes")) {
                    Path path = Paths.get(mailWatch.getDocPath());
                    // Создание объекта SendDocument
                    SendDocument sendDocument = new SendDocument();
                    sendDocument.setChatId(String.valueOf(chatId));  // chatId пользователя, которому вы хотите отправить документ
                    sendDocument.setDocument(new InputFile(path.toFile()));
                    sendDocument.setCaption(mailWatch.getBodyAfter());
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    sendDocument.setReplyMarkup(inKey);
                    sendMessage(chatId, mailWatch.getBody());
                    try{

                        execute(sendDocument);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }

                } else if (mailWatch.getDoc().equals("No") &&  mailWatch.getChannel().equals("Yes") && mailWatch.getPhoto().equals("No")) {
                    SendMessage sendMessage1=new SendMessage();
                    sendMessage1.setText(mailWatch.getBodyAfter());
                    sendMessage1.setChatId(String.valueOf(chatId));
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    sendMessage1.setReplyMarkup(inKey);
                    sendMessage(chatId, mailWatch.getBody());
                    try{
                        execute(sendMessage1);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                } else if (mailWatch.getPhoto().equals("Yes")  && mailWatch.getChannel().equals("No")) {
                    Path path = Paths.get(mailWatch.getPicPath());
                    SendPhoto sendPhoto=new SendPhoto();
                    sendPhoto.setChatId(String.valueOf(chatId));
                    sendPhoto.setPhoto(new InputFile(path.toFile()));
                    sendPhoto.setCaption(mailWatch.getBody());
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    sendPhoto.setReplyMarkup(inKey);
                    try{
                        execute(sendPhoto);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                } else if (mailWatch.getPhoto().equals("Yes")  && mailWatch.getChannel().equals("Yes")) {
                    Path path = Paths.get(mailWatch.getPicPath());
                    SendPhoto sendPhoto=new SendPhoto();
                    sendPhoto.setChatId(String.valueOf(chatId));
                    sendPhoto.setPhoto(new InputFile(path.toFile()));
                    sendPhoto.setCaption(mailWatch.getBodyAfter());
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    sendPhoto.setReplyMarkup(inKey);
                    sendMessage(chatId, mailWatch.getBody());
                    try{
                        execute(sendPhoto);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                } else{
                    EditMessageText messageTextMail=new EditMessageText();
                    messageTextMail.setChatId(String.valueOf(chatId));
                    messageTextMail.setText(mailWatch.getBody());
                    messageTextMail.setMessageId((int) messageId);
                    InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline1=new ArrayList<>();
                    List<InlineKeyboardButton> rowInline2=new ArrayList<>();
                    var watch=new InlineKeyboardButton();
                    watch.setText("Отправить");
                    watch.setCallbackData("send");

                    var back=new InlineKeyboardButton();
                    back.setText("Назад");
                    back.setCallbackData("back");


                    rowInline1.add(watch);
                    rowInline2.add(back);

                    rowsInline.add(rowInline1);
                    rowsInline.add(rowInline2);
                    inKey.setKeyboard(rowsInline);
                    messageTextMail.setReplyMarkup(inKey);
                    try{
                        execute(messageTextMail);
                    }catch(TelegramApiException e){
                        System.out.println("We have problem"+e.getMessage());
                    }
                }
                //отправка пользователям всем
            } else if (callBack.equals("send")) {
                Mails mailWatch=getMails();
                MailNow mails1= new MailNow();
                mails1.setChatId(chatId);
                mails1.setPhoto(getMails().getPhoto());
                mails1.setChannel(getMails().getChannel());
                mails1.setBody(getMails().getBody());
                mails1.setTitle(getMails().getTitle());
                mails1.setDate(new Timestamp(System.currentTimeMillis()));
                mails1.setDoc(mailWatch.getDoc());
                if(mailWatch.getDoc().equals("Yes")){
                    mails1.setDocPath(mailWatch.getDocPath());
                }
                if(mailWatch.getPhoto().equals("Yes")) mails1.setPicPath(mailWatch.getPicPath());
                if(channel.equals("Yes")) mails1.setBodyAfter(mailWatch.getBodyAfter());
                else mails1.setBodyAfter("no");
                mail_nowRepository.save(mails1);
                sendMessage(chatId, "Рассылка была отправлена! Если хотите отправит еще одну напишите /list_of_mails\n или же вы можете создать еще одну рассылку /create");
            }
            //проверка сообщение есть ли документ в письме
        }else if (update.hasMessage() && update.getMessage().hasDocument()) {
            Document document = update.getMessage().getDocument();

            // Получение информации о файле
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());

            try {
                File file = execute(getFileMethod);
                String fileUrl = file.getFileUrl(getBotToken());
                String filePath = fileService.saveFileFromUrl(fileUrl);


                try {
                    processUserInput(update.getMessage().getChatId(), filePath);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }


                // Получение InputStream с содержимым файла
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        //проверка сообщение есть ли фото
        else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            // Обработка фотографии
            List<PhotoSize> photos = update.getMessage().getPhoto();
            PhotoSize lastPhoto = photos.get(photos.size() - 1); // Получаем последнюю (обычно самую большую) фотографию

            String chatId = update.getMessage().getChatId().toString();
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(lastPhoto.getFileId());

            try {
                File file = execute(getFileMethod);
                String fileUrl = file.getFileUrl(getBotToken());
                String filePath = picService.saveFileFromUrl(fileUrl);

                try {
                    processUserInput(update.getMessage().getChatId(), filePath);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                // Получение InputStream с содержимым файла
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

        }
    }
    //начала всего если написал старт
    private void startCommand(long chatId, String name){
        String text="Hi, "+name+", nice to meet you! My name is AccountantBot," +
                " I am your personal accountant, I can" +
                " record your expenses or income. Let's start";
        sendMessage(chatId, text);
        register_user(chatId, name);
    }
    //отправка сообщений
    private void sendMessage(long chatId, String text){
        SendMessage sendMessage=new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);
        try{
            execute(sendMessage);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //проверка состояния сообщения
    private void processUserInput(long chatId, String messageText) throws ParseException {
        BotState currentState = getUserState(chatId);

        switch (currentState) {
            case WAITING_FOR_CREATE:

                sendMessage(chatId, "Во сколько вы хотите отправить эту расслыку? Напишите пожалуйста в формате год-месяц-день часы:минуты");
                String body=messageText;
                setUserState(chatId, BotState.WAITING_FOR_TIME);
                setBody(body);


                break;
            case WAITING_FOR_TIME:
                if(doc.equals("Yes")){
                    sendMessage(chatId, "Дайте документ для рассылки");
                    setUserState(chatId, BotState.WAITING_FOR_DOC);
                } else if (pic.equals("Yes")) {
                    sendMessage(chatId, "Дайте фото для рассылки");
                    setUserState(chatId, BotState.WAITING_FOR_Image);
                } else if (channel.equals("Yes")) {
                    sendMessage(chatId, "После проверки что вы хотите отправить? Давайте для этого сообщение сделаем критерии тоже");
                    create_channeltext2(chatId);
                } else {
                    sendMessage(chatId, "Информация сохранена");
                    setTime(messageText);
                    create_Mail(chatId);
                }
                setTime(messageText);
                break;
            case WAITING_FOR_TITLE:
                sendMessage(chatId, "Теперь напишите текст для рассылки");
                setUserState(chatId, BotState.WAITING_FOR_CREATE);
                setTitle(messageText);
                break;
            case WAITING_FOR_DOC:
                sendMessage(chatId, "Информация сохранена");
                channelAfter="No";
                setDocContent(messageText);
                create_Mail(chatId);
                break;
            case WAITING_FOR_Image:
                sendMessage(chatId, "Информация сохранена");
                channelAfter="No";
                setPicContemt(messageText);
                create_Mail(chatId);
                break;
            case WAITING_FOR_ChannelText:
                if(doc.equals("Yes")){
                    sendMessage(chatId, "Дайте документ для рассылки");
                    setUserState(chatId, BotState.WAITING_FOR_DOC);
                    setBodyChannel(messageText);
                } else if (pic.equals("Yes")) {
                    sendMessage(chatId, "Дайте фото для рассылки");
                    setUserState(chatId, BotState.WAITING_FOR_Image);
                    setBodyChannel(messageText);
                }
                else {
                    sendMessage(chatId, "Информация сохранена");
                    setBodyChannel(messageText);
                    channelAfter="No";
                    create_Mail(chatId);
                }
                break;
            default:
                String Idk="I dont know this function";
                sendMessage(chatId, Idk);
                break;

        }
    }
    //меняем состояние сообщение
    private void setUserState(long chatId, BotState state) {
        userStates.put(chatId, state);
    }
    //получение состояние сообщение
    private BotState getUserState(long chatId) {
        return userStates.getOrDefault(chatId, BotState.DEFAULT);
    }
    //удаление соостояние
    private void resetUserState(long chatId) {
        userStates.remove(chatId);
    }
    //регистрация пользователей то есть сохранение его в бд
    private void register_user(long ChatId, String name){
        User users=new User();
        users.setChatId(ChatId);
        users.setName(name);
        users.setTimestamp(new Timestamp(System.currentTimeMillis()));
        userRepository.save(users);
    }
    //получение текста, времени, и название
    private void setBody(String body){this.body=body;}
    private String getBody(){
        return body;
    }
    private void setTime(String time){this.time=time;}
    private String getTime(){
        return time;
    }
    private void setTitle(String title){this.title=title;}
    private String getTitle(){return title;}
    //запись всех данных в бд
    private void create_Mail(long chatId){
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        Mails mails=new Mails();
        if (getTime() != null && !getTime().isEmpty()) {
            try {
                Date parsedDate = dateFormatter.parse(getTime());
                Timestamp timestamp = new Timestamp(parsedDate.getTime());
                mails.setDate(timestamp);
            } catch (ParseException e) {
                sendMessage(chatId, "plz write correct date");
                setUserState(chatId, BotState.WAITING_FOR_TIME);
            }
        } else {
            sendMessage(chatId, "plz write correct date");
            setUserState(chatId, BotState.WAITING_FOR_TIME);
        }

        mails.setChatId(chatId);
        mails.setTitle(getTitle());
        mails.setBody(getBody());
        mails.setPhoto(pic);
        mails.setChannel(channel);
        mails.setDoc(doc);
        if(doc.equals("Yes")){
            mails.setDocPath(getDocContent());
        }
        else mails.setDocPath("No");

        if(pic.equals("Yes")) mails.setPicPath(getPicContemt());
        else mails.setPicPath("No");
        if(channel.equals("Yes")) mails.setBodyAfter(getBodyChannel());
        else mails.setBodyAfter("no");
        mailsRepository.save(mails);


    }
    //критерий для рассылки
    private void create(long chatId){
        SendMessage sendMsg=new SendMessage();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Давайте сперва поставим критерии для рассылки как например нужно ли добавить картинку?\n Нажмите кнопки для их выбора.\n Если все идеально нажмите кнопку все идеально");
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_1=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_3=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_4=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_5=new ArrayList<>();
        var Picbutton=new InlineKeyboardButton();
        Picbutton.setText("Картина: "+pic);
        Picbutton.setCallbackData("Pic");


        var channelButton=new InlineKeyboardButton();
        channelButton.setText("Проверка на подписку: "+channel);
        channelButton.setCallbackData("Channel");

        var docButton=new InlineKeyboardButton();
        docButton.setText("Документ(Файл): "+doc);
        docButton.setCallbackData("Doc");

        var ExitButton=new InlineKeyboardButton();
        ExitButton.setText("Все идельно");
        ExitButton.setCallbackData("Ideal");

        rowInline_1.add(Picbutton);
        rowInline_3.add(channelButton);
        rowInline_4.add(docButton);
        rowInline_5.add(ExitButton);

        rowsInline.add(rowInline_1);
        rowsInline.add(rowInline_3);
        rowsInline.add(rowInline_4);
        rowsInline.add(rowInline_5);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //критерий для text after check
    private void create_channeltext(long chatId){
        SendMessage sendMsg=new SendMessage();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Давайте сперва поставим критерии для рассылки как например нужно ли добавить картинку?\n Нажмите кнопки для их выбора.\n Если все идеально нажмите кнопку все идеально");
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_1=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_4=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_5=new ArrayList<>();
        var Picbutton=new InlineKeyboardButton();
        Picbutton.setText("Картина: "+pic);
        Picbutton.setCallbackData("Pic");



        var docButton=new InlineKeyboardButton();
        docButton.setText("Документ(Файл): "+doc);
        docButton.setCallbackData("Doc");

        var ExitButton=new InlineKeyboardButton();
        ExitButton.setText("Все идельно");
        ExitButton.setCallbackData("IdealChannel");

        rowInline_1.add(Picbutton);
        rowInline_4.add(docButton);
        rowInline_5.add(ExitButton);

        rowsInline.add(rowInline_1);
        rowsInline.add(rowInline_4);
        rowsInline.add(rowInline_5);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    private void create_channeltext2(long chatId){
        SendMessage sendMsg=new SendMessage();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Давайте сперва поставим критерии для рассылки как например нужно ли добавить картинку?\n Нажмите кнопки для их выбора.\n Если все идеально нажмите кнопку все идеально");
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_1=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_4=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_5=new ArrayList<>();
        var Picbutton=new InlineKeyboardButton();
        Picbutton.setText("Картина: "+pic);
        Picbutton.setCallbackData("Pic2");



        var docButton=new InlineKeyboardButton();
        docButton.setText("Документ(Файл): "+doc);
        docButton.setCallbackData("Doc2");

        var ExitButton=new InlineKeyboardButton();
        ExitButton.setText("Все идельно");
        ExitButton.setCallbackData("IdealChannel");

        rowInline_1.add(Picbutton);
        rowInline_4.add(docButton);
        rowInline_5.add(ExitButton);

        rowsInline.add(rowInline_1);
        rowsInline.add(rowInline_4);
        rowsInline.add(rowInline_5);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //при выборе нет  или да во время выбирание критерий
    private void Yes_NO(long chatId, long messageId, String text, InlineKeyboardButton Yes, InlineKeyboardButton No){
        EditMessageText messageText=new EditMessageText();
        messageText.setChatId(String.valueOf(chatId));
        messageText.setText(text);
        messageText.setMessageId((int) messageId);

        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline=new ArrayList<>();
        rowInline.add(Yes);
        rowInline.add(No);
        rowsInline.add(rowInline);
        inKey.setKeyboard(rowsInline);
        messageText.setReplyMarkup(inKey);
        try{
            execute(messageText);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //тот же критерий но с EditMessageText вместо SendMessage
    private void create_edit(long chatId, long messageId){
        EditMessageText sendMsg=new EditMessageText();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Давайте сперва поставим критерии для рассылки как например нужно ли добавить картинку?\n Нажмите кнопки для их выбора.\n Если все идеально нажмите кнопку все идеально");
        sendMsg.setMessageId((int) messageId);
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_1=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_3=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_4=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_5=new ArrayList<>();

        var Picbutton=new InlineKeyboardButton();
        Picbutton.setText("Картина: "+pic);
        Picbutton.setCallbackData("Pic");


        var channelButton=new InlineKeyboardButton();
        channelButton.setText("Проверка на подписку: "+channel);
        channelButton.setCallbackData("Channel");

        var docButton=new InlineKeyboardButton();
        docButton.setText("Документ(Файл): "+doc);
        docButton.setCallbackData("Doc");

        var ExitButton=new InlineKeyboardButton();
        ExitButton.setText("Все идельно");
        ExitButton.setCallbackData("Ideal");

        rowInline_1.add(Picbutton);
        rowInline_3.add(channelButton);
        rowInline_4.add(docButton);
        rowInline_5.add(ExitButton);

        rowsInline.add(rowInline_1);
        rowsInline.add(rowInline_3);
        rowsInline.add(rowInline_4);
        rowsInline.add(rowInline_5);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    private void create_edit_channel(long chatId, long messageId){
        EditMessageText sendMsg=new EditMessageText();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Давайте сперва поставим критерии для рассылки как например нужно ли добавить картинку?\n Нажмите кнопки для их выбора.\n Если все идеально нажмите кнопку все идеально");
        sendMsg.setMessageId((int) messageId);
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_1=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_4=new ArrayList<>();
        List<InlineKeyboardButton> rowInline_5=new ArrayList<>();

        var Picbutton=new InlineKeyboardButton();
        Picbutton.setText("Картина: "+pic);
        Picbutton.setCallbackData("Pic2");



        var docButton=new InlineKeyboardButton();
        docButton.setText("Документ(Файл): "+doc);
        docButton.setCallbackData("Doc2");

        var ExitButton=new InlineKeyboardButton();
        ExitButton.setText("Все идельно");
        ExitButton.setCallbackData("IdealChannel");

        rowInline_1.add(Picbutton);
        rowInline_4.add(docButton);
        rowInline_5.add(ExitButton);

        rowsInline.add(rowInline_1);
        rowsInline.add(rowInline_4);
        rowsInline.add(rowInline_5);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //удаление всех элементов
    //в будущем сделать удаление только одного
    private void delete_mail(long chatId){
        SendMessage sendMsg=new SendMessage();
        sendMsg.setChatId(String.valueOf(chatId));
        sendMsg.setText("Are you sure? All dates will delete!");
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline=new ArrayList<>();
        var yesbutton=new InlineKeyboardButton();
        yesbutton.setText("Yes");
        yesbutton.setCallbackData("Yes_Delete");
        var nobutton=new InlineKeyboardButton();
        nobutton.setText("No");
        nobutton.setCallbackData("No_Delete");
        rowInline.add(yesbutton);
        rowInline.add(nobutton);
        rowsInline.add(rowInline);
        inKey.setKeyboard(rowsInline);
        sendMsg.setReplyMarkup(inKey);
        try{
            execute(sendMsg);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }
    //создание листа всех рассылок
    private void list_mail(long chatId){
        String text="Ваши готовые рассылки:\n";
        int i=1;
        var mails=mailsRepository.findAll();
        ArrayList<String> mail_array=new ArrayList<>();
        boolean isHave=false;
        for (Mails mail:mails) {
            if(mail.getChatId()==chatId && mail.getDate()!=null){
                text+=i+"."+mail.getTitle()+"\n";
                i+=1;
                isHave=true;
                mail_array.add(mail.getTitle());
                mailsList.add(mail);
            }
        }
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        for (int j = 0; j < mail_array.size(); j++) {
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            var title_mail=new InlineKeyboardButton();
            title_mail.setText((j+1)+"."+mail_array.get(j));
            title_mail.setCallbackData("Mail_"+(j+1));
            mail_callback.add("Mail_"+(j+1));
            rowInline.add(title_mail);
            rowsInline.add(rowInline);
        }


        if(!isHave) sendMessage(chatId, "Готовых рассылок еще нету");
        else {
            text+="Если хотите посмотреть подробно рассылку или же отправить его нажмите на его кнопку";
            var exit=new InlineKeyboardButton();
            exit.setText("Ок");
            exit.setCallbackData("Exit");
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            rowInline.add(exit);
            rowsInline.add(rowInline);
            SendMessage sendMsg=new SendMessage();
            sendMsg.setChatId(String.valueOf(chatId));
            sendMsg.setText(text);
            inKey.setKeyboard(rowsInline);
            sendMsg.setReplyMarkup(inKey);
            try{
                execute(sendMsg);
            }catch(TelegramApiException e){
                System.out.println("We have problem "+e.getMessage());
            }
        }
    }

    public Mails getMails() {
        return mails;
    }

    public void setMails(Mails mails) {
        this.mails = mails;
    }
    //возвращение назад в список
    private void back(long chatId, long messageId){
        String text="Ваши готовые рассылки:\n";
        int i=1;
        var mails=mailsRepository.findAll();
        ArrayList<String> mail_array=new ArrayList<>();
        boolean isHave=false;
        for (Mails mail:mails) {
            if(mail.getChatId()==chatId){
                text+=i+"."+mail.getTitle()+"\n";
                i+=1;
                isHave=true;
                mail_array.add(mail.getTitle());
                mailsList.add(mail);
            }
        }
        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        for (int j = 0; j < mail_array.size(); j++) {
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            var title_mail=new InlineKeyboardButton();
            title_mail.setText((j+1)+"."+mail_array.get(j));
            title_mail.setCallbackData("Mail_"+(j+1));
            mail_callback.add("Mail_"+(j+1));
            rowInline.add(title_mail);
            rowsInline.add(rowInline);
        }


        if(!isHave) sendMessage(chatId, "Готовых рассылок еще нету");
        else {
            text+="Если хотите посмотреть подробно рассылку или же отправить его нажмите на его кнопку";
            var exit=new InlineKeyboardButton();
            exit.setText("Ок");
            exit.setCallbackData("Exit");
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            rowInline.add(exit);
            rowsInline.add(rowInline);
            EditMessageText messageTextMail=new EditMessageText();
            messageTextMail.setChatId(String.valueOf(chatId));
            messageTextMail.setText(text);
            messageTextMail.setMessageId((int) messageId);
            inKey.setKeyboard(rowsInline);
            messageTextMail.setReplyMarkup(inKey);
            try{
                execute(messageTextMail);
            }catch(TelegramApiException e){
                System.out.println("We have problem "+e.getMessage());
            }
        }
    }

    public String getDocContent() {
        return docContent;
    }
    @Transactional
    public void setDocContent(String path){
        this.docContent = path;
    }

    public String getPicContemt() {
        return picContemt;
    }

    public void setPicContemt(String picContemt) {
        this.picContemt = picContemt;
    }


    public String getBodyChannel() {
        return bodyChannel;
    }

    public void setBodyChannel(String bodyChannel) {
        this.bodyChannel = bodyChannel;
    }
}

