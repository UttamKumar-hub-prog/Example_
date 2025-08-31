package com.wipro.NotificationService.services;

import java.time.LocalDateTime;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wipro.NotificationService.entities.Notification;
import com.wipro.NotificationService.repositorys.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

	private final NotificationRepository notificationRepository;
	private final JavaMailSender mailSender;

	@Transactional
	public Notification saveNotification(Long paymentId, Long userId, String message) {
		Notification notification = new Notification();
		notification.setPaymentId(paymentId);
		notification.setUserId(userId);
		notification.setMessage(message);
		notification.setStatus("PENDING");
		notification.setCreatedAt(LocalDateTime.now());

		return notificationRepository.save(notification);
	}

	@Async
	public void sendEmail(Long paymentId, Long userId, String recipientEmail, String message) {
		Notification notification = saveNotification(paymentId, userId, message); // save first
		try {
			SimpleMailMessage mailMessage = new SimpleMailMessage();
			mailMessage.setTo(recipientEmail);
			mailMessage.setSubject("Payment Notification");
			mailMessage.setText(message);

			mailSender.send(mailMessage);

			// Update status
			notification.setStatus("SENT");
			notificationRepository.save(notification);

			System.out.println("Email sent to user " + userId + ": " + message);

		} catch (Exception e) {
			// Update status instead of saving new
			notification.setStatus("FAILED");
			notificationRepository.save(notification);

			System.err.println("Failed to send email to user " + userId + ": " + e.getMessage());
			e.printStackTrace(); // Print full stack trace for debugging
		}
	}

}