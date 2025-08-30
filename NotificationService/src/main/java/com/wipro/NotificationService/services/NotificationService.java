package com.wipro.NotificationService.services;

public interface NotificationService  {
 

	void sendEmail(Long paymentId, Long receiverId, String receiverEmail, String message);

}
