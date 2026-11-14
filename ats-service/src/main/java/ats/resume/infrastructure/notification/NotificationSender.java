package ats.resume.infrastructure.notification;

/** Канал уведомлений. Реализация не должна логировать текст сообщения. */
public interface NotificationSender {

    void send(String text) throws NotificationException;
}
