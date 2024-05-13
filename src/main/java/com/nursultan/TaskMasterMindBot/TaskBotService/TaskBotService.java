package com.nursultan.TaskMasterMindBot.TaskBotService;

import com.nursultan.TaskMasterMindBot.TaskBotConfig.TaskBotConfig;
import com.nursultan.TaskMasterMindBot.TaskBotModel.Task;
import com.nursultan.TaskMasterMindBot.TaskBotModel.TaskRepository;
import com.nursultan.TaskMasterMindBot.TaskBotModel.User;
import com.nursultan.TaskMasterMindBot.TaskBotModel.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskBotService extends TelegramLongPollingBot {

    private static final String DUE_DATE_BUTTON = "Due Date";
    private static final String YES_BUTTON = "Yes";
    private static final String NO_BUTTON = "No";
    private static final String HIGH_BUTTON = "High";
    private static final String MEDIUM_BUTTON = "Medium";
    private static final String LOW_BUTTON = "Low";
    private static final String VIEW_LIST_BUTTON = "List of tasks";

    private static final String DELETE_TASKS_BUTTON = "Delete tasks";
    private static final String DELETE_BUTTON = "Delete ";
    private static final String BACK_TO_LIST = "Back to list of tasks";

    Task task = new Task();
    private static final String DESCRIPTION_BUTTON = "Description";
    private final TaskBotConfig taskBotConfig;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String HELP_TEXT = "Need assistance? The /help command is here for you! " +
            "Explore the following commands to supercharge your task management:\n\n" +
            "/start - \uD83D\uDE80 get a welcome message\n" +
            "/create - \uD83D\uDCDD add new tasks effortlessly\n" +
            "/list - \uD83D\uDDC2\uFE0F view and manage all your tasks in one place\n";

    private static final String NAME_BUTTON = "Name";

    private Map<Long, UserState> userStates = new HashMap<>();

    public TaskBotService(TaskBotConfig taskBotConfig) {
        this.taskBotConfig = taskBotConfig;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/help", "get info how to use the bot"));
        listOfCommands.add(new BotCommand("/list", "display a list of all tasks"));
        listOfCommands.add(new BotCommand("/create", "add new tasks to the list"));
        listOfCommands.add(new BotCommand("/delete", "choose a task to delete from the list"));

        try{
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e){

        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText() && userStates.isEmpty()) {
            handleUserCommands(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleUserStateCallback(update.getMessage());
        }
    }

    private void handleUserCommands(Message message){
        String command = message.getText();
        long chatId = message.getChatId();

        Map<String, Runnable> commandActions = Map.of(
                "/start", () -> startCommandReceived(chatId, message.getChat().getFirstName(), message),
                "/help", () -> sendMessage(chatId, HELP_TEXT),
                "/create", () -> createTask(chatId),
                "/list", () -> listTasks(chatId),
                "/delete", () -> viewDeleteTasks(chatId)
        );
        commandActions.getOrDefault(command, () -> sendMessage(chatId, "Command was not recognized")).run();
    }

    private void viewDeleteTasks(long chatId) {
        List<Task> tasks = taskRepository.findAll();

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();

        for(Task task: tasks){
            List<InlineKeyboardButton> inlineRow = new ArrayList<>();

            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText(DELETE_BUTTON + task.getName());
            deleteButton.setCallbackData("DELETE_TASK_" + task.getId());

            inlineRow.add(deleteButton);
            inlineRows.add(inlineRow);
        }

        InlineKeyboardButton backToList = new InlineKeyboardButton();
        backToList.setText(BACK_TO_LIST);
        backToList.setCallbackData(BACK_TO_LIST);
        List<InlineKeyboardButton> inlineRow = new ArrayList<>();
        inlineRow.add(backToList);
        inlineRows.add(inlineRow);

        inlineKeyboardMarkup.setKeyboard(inlineRows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Choose a task to delete: ");
        message.setReplyMarkup(inlineKeyboardMarkup);

        executeMessage(message);
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery){
        String callBackData = callbackQuery.getData();
        long messageId = callbackQuery.getMessage().getMessageId();
        long chatId = callbackQuery.getMessage().getChatId();


        if(callBackData.equals(NAME_BUTTON)){
            String text = "Enter task name: ";
            executeEditMessageText(text, chatId, messageId);
            userStates.put(chatId, UserState.ENTER_TASK_NAME);
        }
        if(callBackData.equals(DESCRIPTION_BUTTON)){
            String text = "Enter task description: ";
            executeEditMessageText(text, chatId, messageId);
            userStates.put(chatId, UserState.ENTER_TASK_DESCRIPTION);
        }
        if(callBackData.equals(DUE_DATE_BUTTON)){
            String text = "Enter task due date in format yyyy-mm-dd: ";
            executeEditMessageText(text, chatId, messageId);
            userStates.put(chatId, UserState.ENTER_TASK_DUE_DATE);
        }
        if(callBackData.equals(YES_BUTTON)){
            String text = "Choose task priority: ";
            chooseTaskPriority(text, chatId, messageId);
        }
        if(callBackData.equals(NO_BUTTON)){
            String text = "View the list of tasks: ";
            viewListTasks(text, chatId, messageId);
            userStates.put(chatId, UserState.ENTER_VIEW_LIST);
        }
        if(callBackData.equals(HIGH_BUTTON)){
            String text = "View the list of tasks: ";
            viewListTasks(text, chatId, messageId);
            saveTaskPriority(chatId, HIGH_BUTTON);
            userStates.put(chatId, UserState.ENTER_VIEW_LIST);
        }
        if(callBackData.equals(MEDIUM_BUTTON)){
            String text = "View the list of tasks: ";
            viewListTasks(text, chatId, messageId);
            saveTaskPriority(chatId, MEDIUM_BUTTON);
            userStates.put(chatId, UserState.ENTER_VIEW_LIST);
        }
        if(callBackData.equals(LOW_BUTTON)){
            String text = "View the list of tasks: ";
            viewListTasks(text, chatId, messageId);
            saveTaskPriority(chatId, LOW_BUTTON);
            userStates.put(chatId, UserState.ENTER_VIEW_LIST);
        }
        if(callBackData.equals(VIEW_LIST_BUTTON)){
            listTasks(chatId);
            userStates.remove(chatId);
        }
        if(callBackData.equals(DELETE_TASKS_BUTTON)){
            viewDeleteTasks(chatId);
            userStates.remove(chatId);
        }
        if(callBackData.startsWith("DELETE_TASK")){
            String taskIdStr = callBackData.replace("DELETE_TASK_", "");
            try{
                long taskId = Long.parseLong(taskIdStr);
                deleteTask(chatId, taskId);
            } catch (NumberFormatException e){
                sendMessage(chatId, "Invalid task ID");
            }
        }
        if(callBackData.equals(BACK_TO_LIST)){
            listTasks(chatId);
            userStates.remove(chatId);
        }

    }

    private void handleUserStateCallback(Message message){
        long chatId = message.getChatId();

        UserState userState = userStates.getOrDefault(chatId, null);

        if(userState == UserState.ENTER_TASK_NAME) {
            String taskName = message.getText();
            saveTaskName(chatId, taskName);
            userStates.remove(chatId);
        }
        if(userState == UserState.ENTER_TASK_DESCRIPTION){
            String taskDescription = message.getText();
            saveTaskDescription(chatId, taskDescription);
            userStates.remove(chatId);
        }
        if(userState == UserState.ENTER_TASK_DUE_DATE){
            String taskDueDate = message.getText();
            saveTaskDueDate(chatId, taskDueDate);
            userStates.remove(chatId);
        }
        if(userState == UserState.ENTER_TASK_PRIORITY){
            userStates.remove(chatId);
        }
        if(userState == UserState.ENTER_DELETE_VIEW_LIST){
            userStates.remove(chatId);
        }
    }

    private void deleteTask(long chatId, long taskId){
        Optional<Task> optionalTask = taskRepository.findByIdAndUser_ChatId(taskId, chatId);

        if(optionalTask.isPresent()) {
            Task taskToDelete = optionalTask.get();

            taskRepository.delete(taskToDelete);

            sendMessage(chatId, "Task \"" + taskToDelete.getName() + "\" has been deleted");
        } else {
            sendMessage(chatId, "Task with ID " + taskId + " not found");
        }
    }

    private void viewListTasks(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setMessageId((int) messageId);
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();
        List<InlineKeyboardButton> inlineRow = new ArrayList<>();

        var viewButton = new InlineKeyboardButton();

        viewButton.setText(VIEW_LIST_BUTTON);
        viewButton.setCallbackData(VIEW_LIST_BUTTON);

        inlineRow.add(viewButton);
        inlineRows.add(inlineRow);

        inlineKeyboardMarkup.setKeyboard(inlineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e){

        }
    }

    private void listTasks(long chatId) {
        User user = userRepository.findById(chatId);
        List<Task> tasks = taskRepository.findByUser(user);

        Comparator<Task> priorityComparator = Comparator.
                comparing(Task::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));

        List<Task> sortedTasks = tasks.stream().sorted(priorityComparator).collect(Collectors.toList());

        if(!sortedTasks.isEmpty()){
            StringBuilder messageText = new StringBuilder("List of Tasks:\n");

            for(Task task: sortedTasks) {
                messageText.append("*").append(task.getName()).append("*")
                        .append(": ").append(task.getDescription()).append(" (Priority: ")
                        .append(task.getPriority()).append(")\n");
            }

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(messageText.toString());
            message.setParseMode("Markdown");
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();
            List<InlineKeyboardButton> inlineRow = new ArrayList<>();

            var deleteTasks = new InlineKeyboardButton();

            deleteTasks.setText(DELETE_TASKS_BUTTON);
            deleteTasks.setCallbackData(DELETE_TASKS_BUTTON);

            inlineRow.add(deleteTasks);
            inlineRows.add(inlineRow);

            inlineKeyboardMarkup.setKeyboard(inlineRows);
            message.setReplyMarkup(inlineKeyboardMarkup);

            executeMessage(message);
        } else {
            sendMessage(chatId, "No tasks found");
        }
    }

    private void saveTaskPriority(long chatId, String button) {
        Task.Priority buttonEnum = Task.Priority.valueOf(button);
        this.task.setPriority(buttonEnum);
        taskRepository.save(this.task);
    }

    private void chooseTaskPriority(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setMessageId((int) messageId);
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();
        List<InlineKeyboardButton> inlineRow = new ArrayList<>();

        var highButton = new InlineKeyboardButton();
        var mediumButton = new InlineKeyboardButton();
        var lowButton = new InlineKeyboardButton();

        highButton.setText(HIGH_BUTTON);
        highButton.setCallbackData(HIGH_BUTTON);

        mediumButton.setText(MEDIUM_BUTTON);
        mediumButton.setCallbackData(MEDIUM_BUTTON);

        lowButton.setText(LOW_BUTTON);
        lowButton.setCallbackData(LOW_BUTTON);

        inlineRow.add(highButton);
        inlineRow.add(mediumButton);
        inlineRow.add(lowButton);
        inlineRows.add(inlineRow);

        inlineKeyboardMarkup.setKeyboard(inlineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e){

        }
    }

    private void saveTaskDueDate(long chatId, String taskDueDate) {
        LocalDate dueDate = LocalDate.parse(taskDueDate);
        this.task.setDueDate(dueDate);
        taskRepository.save(this.task);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Task *" + this.task.getName() + "* created!\n" +
                        "Do you want to set task priority?");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();
        List<InlineKeyboardButton> inlineRow = new ArrayList<>();

        var yesButton = new InlineKeyboardButton();
        var noButton = new InlineKeyboardButton();

        yesButton.setText(YES_BUTTON);
        yesButton.setCallbackData(YES_BUTTON);

        noButton.setText(NO_BUTTON);
        noButton.setCallbackData(NO_BUTTON);

        inlineRow.add(yesButton);
        inlineRow.add(noButton);
        inlineRows.add(inlineRow);

        inlineKeyboardMarkup.setKeyboard(inlineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        executeMessage(message);
    }

    private void saveTaskDescription(long chatId, String taskDescription) {
        this.task.setDescription(taskDescription);
        taskRepository.save(this.task);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Provide a task due date in format yyyy-mm-dd: ");

        setInlineKeyboard(message, DUE_DATE_BUTTON);

        executeMessage(message);
    }

    private void saveTaskName(long chatId, String taskName){
        User user = userRepository.findById(chatId);

        Optional<Task> existingTask = taskRepository.findByName(taskName);

        if(existingTask.isPresent()){
            sendMessage(chatId, "Task with name '" + taskName + "' already exists. Please choose a different name");
            userStates.put(chatId, UserState.ENTER_TASK_NAME);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Provide a new and unique task name: ");

            setInlineKeyboard(message, NAME_BUTTON);

            executeMessage(message);
            return;
        }

        this.task.setName(taskName);
        this.task.setUser(user);

        taskRepository.save(this.task);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Provide a task description: ");

        setInlineKeyboard(message, DESCRIPTION_BUTTON);

        executeMessage(message);
    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setMessageId((int) messageId);
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e){

        }
    }

    private void createTask(long chatId){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        User user = userRepository.findById(chatId);

        Task task = new Task();
        task.setUser(user);
        userStates.put(chatId, UserState.ENTER_TASK_NAME);

        message.setText("Provide a task name: ");

        setInlineKeyboard(message, NAME_BUTTON);

        executeMessage(message);

    }

    private void setInlineKeyboard(SendMessage message, String button){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inlineRows = new ArrayList<>();
        List<InlineKeyboardButton> inlineRow = new ArrayList<>();

        var buttonKeyboard = new InlineKeyboardButton();

        buttonKeyboard.setText(button);
        buttonKeyboard.setCallbackData(button);

        inlineRow.add(buttonKeyboard);
        inlineRows.add(inlineRow);

        inlineKeyboardMarkup.setKeyboard(inlineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);
    }

    private void registerUser(Message message){
        if(userRepository.findById(message.getChatId()).isEmpty()){
            var chatId = message.getChatId();
            var chat = message.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
        }
    }

    private void startCommandReceived(long chatId, String firstName, Message message) {
        registerUser(message);
        String answer = EmojiParser.parseToUnicode("Welcome, " + firstName + ", nice to meet you!" + " :blush:");
        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {

        }
    }

    @Override
    public String getBotUsername() {
        return taskBotConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return taskBotConfig.getBotToken();
    }

    public enum UserState{
        ENTER_TASK_DESCRIPTION, ENTER_TASK_DUE_DATE, ENTER_TASK_PRIORITY, ENTER_VIEW_LIST, ENTER_DELETE_VIEW_LIST, ENTER_TASK_NAME
    }
}
