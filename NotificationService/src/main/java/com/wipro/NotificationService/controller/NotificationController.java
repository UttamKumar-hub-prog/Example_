package com.wipro.NotificationService.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wipro.NotificationService.services.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	// Example endpoint to send a notification via email
	@PostMapping("/send")
	public ResponseEntity<String> sendNotification(@RequestParam Long paymentId, @RequestParam Long userId,
			@RequestParam String email, @RequestParam String message) {

		notificationService.sendEmail(paymentId, userId, email, message);
		return ResponseEntity.ok("Notification processing started for user " + userId);
	}

}
