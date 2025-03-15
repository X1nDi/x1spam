package ru.x1ndi.x1spam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class X1spam extends JavaPlugin implements Listener {

    private final Map<String, MessageTracker> messageTrackers = new HashMap<>();
    private String muteCommand;
    private int spamLimit;
    private long timeLimit; // В миллисекундах
    private String muteMessage;
    private final String bypassPermission = "x1spam.bypass"; // Жестко заданное разрешение
    private static int minMessageLength; // Минимальная длина сообщения
    private static int similarityThreshold; // Порог схожести в процентах

    @Override
    public void onEnable() {
        saveDefaultConfig();
        muteCommand = getConfig().getString("mute-command", "mute %player% 30m Спам!");
        spamLimit = getConfig().getInt("spam-limit", 4);
        timeLimit = getConfig().getLong("time-limit", 60) * 1000; // Преобразуем секунды в миллисекунды
        muteMessage = getConfig().getString("mute-message", "Вы были замучены за спам!");
        minMessageLength = getConfig().getInt("min-message-length", 4); // Получаем минимальную длину сообщения
        similarityThreshold = getConfig().getInt("similarity-threshold", 80); // Получаем порог схожести
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String message = event.getMessage();

        // Проверяем, было ли сообщение отправлено
        if (message == null || message.isEmpty()) {
            return; // Игнорируем пустые сообщения
        }

        // Проверяем наличие разрешения на игнорирование спама
        if (player.hasPermission(bypassPermission)) {
            return; // Если есть разрешение, выходим из метода
        }

        MessageTracker tracker = messageTrackers.computeIfAbsent(playerName, k -> new MessageTracker(spamLimit, timeLimit));

        // Добавляем сообщение только если оно не было отменено
        if (!event.isCancelled()) {
            tracker.addMessage(message);
        }

        if (tracker.isSpamming()) {
            String command = muteCommand.replace("%player%", playerName);
            // Переносим выполнение команды в основной поток
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                player.sendMessage(ChatColor.RED + muteMessage);
                event.setCancelled(true); // Отменяем событие, чтобы сообщение не прошло в чат
            });
            messageTrackers.remove(playerName); // Удаляем трекер после мута
        }
    }

    private static class MessageTracker {
        private final int spamLimit;
        private final long timeLimit;
        private final String[] lastMessages;
        private int index = 0;
        private long lastMessageTime = 0;

        public MessageTracker(int spamLimit, long timeLimit) {
            this.spamLimit = spamLimit;
            this.timeLimit = timeLimit;
            this.lastMessages = new String[spamLimit];
        }

        public void addMessage(String message) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMessageTime > timeLimit) {
                index = 0; // Сброс, если прошло больше времени
            }
            lastMessages[index % spamLimit] = message;
            index++;
            lastMessageTime = currentTime;
        }

        public boolean isSpamming() {
            if (index < spamLimit) return false;

            String firstMessage = lastMessages[0];
            for (int i = 1; i < spamLimit; i++) {
                if (!isSimilar(firstMessage, lastMessages[i])) {
                    return false;
                }
            }
            return true;
        }

        private boolean isSimilar(String msg1, String msg2) {
            // Находим длину более короткого сообщения
            int minLength = Math.min(msg1.length(), msg2.length());

            // Если одно из них короче минимальной длины, считаем их разными при условии полного равенства
            if (msg1.length() <= minMessageLength & msg2.length() <= minMessageLength) {
                return msg1.equals(msg2); // Сравниваем на полное равенство
            }

            // Устанавливаем порог для схожести (80% от длины более короткого сообщения)
            int threshold = (int) (minLength * (similarityThreshold / 100.0)); // 80% от длины

            // Считаем количество различий
            int differences = 0;

            // Сравниваем символы в обоих сообщениях
            for (int i = 0; i < minLength; i++) {
                if (msg1.charAt(i) != msg2.charAt(i)) {
                    differences++;
                    if (differences > threshold) {
                        return false; // Если различий больше порога, считаем сообщения разными
                    }
                }
            }

            return differences <= threshold; // Если общее количество различий меньше или равно порогу, считаем сообщения схожими
        }
    }
}