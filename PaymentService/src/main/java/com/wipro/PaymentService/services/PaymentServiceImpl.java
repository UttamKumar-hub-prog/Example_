package com.wipro.PaymentService.services;

import java.time.LocalDateTime;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wipro.PaymentService.dto.AccountDTO;
import com.wipro.PaymentService.dto.AuditLogDTO;
import com.wipro.PaymentService.dto.CustomerDTO;
import com.wipro.PaymentService.dto.PaymentEventDTO;
import com.wipro.PaymentService.entities.Payment;
import com.wipro.PaymentService.entities.PaymentStatus;
import com.wipro.PaymentService.exceptions.InsufficientBalanceException;
import com.wipro.PaymentService.exceptions.PaymentProcessingException;
import com.wipro.PaymentService.feign.AccountClient;
import com.wipro.PaymentService.feign.AuditLogClient;
import com.wipro.PaymentService.feign.CustomerClient;
import com.wipro.PaymentService.repositorys.PaymentRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

	private final CustomerClient customerClient;
	private final AccountClient accountClient;
	private final AuditLogClient auditLogClient;

	private final PaymentRepository paymentRepository;

	private final KafkaTemplate<String, PaymentEventDTO> kafkaTemplate;
	private final String PAYMENT_TOPIC = "payment-notifications";

	@Override
	@Transactional
	public Payment processPayment(Long senderId, Long receiverId, Long amount) {

		// Validate positive amount
		if (amount == null || amount <= 0) {
			throw new InsufficientBalanceException("Insufficient balance in sender's account");
		}

		// fetch the sender and receiver information
		CustomerDTO sender = customerClient.getCustomerById(senderId);

		AccountDTO senderAccount = accountClient.getAccountDetailsById(sender.getId());

		CustomerDTO receiver = customerClient.getCustomerById(receiverId);

		AccountDTO receiverAccount = accountClient.getAccountDetailsById(receiver.getId());

		// validating the balance if the verify the sender account have insufficient
		// balance

		// Create initial payment record with pending status

		Payment payment = new Payment();
		payment.setSenderName(sender.getName());
		payment.setSenderAccountNumber(sender.getAccountNumber());
		payment.setReceiverName(receiver.getName());
		payment.setReceiverAccountNumber(receiver.getAccountNumber());
		payment.setAmount(amount);
		payment.setPaymentStatus(PaymentStatus.PENDING);

		Payment savedPayment = paymentRepository.save(payment);

		try {
			// debit sender and credit receiver

			senderAccount.setBalance(senderAccount.getBalance() - amount.longValue());
			receiverAccount.setBalance(receiverAccount.getBalance() + amount.longValue());

			accountClient.updateAccount(senderAccount.getId(), senderAccount);
			accountClient.updateAccount(receiverAccount.getId(), receiverAccount);

			// Update payment status to SUCCESS
			savedPayment.setPaymentStatus(PaymentStatus.SUCCESS);

			// After updating sender and receiver accounts
			PaymentEventDTO event = new PaymentEventDTO();
			event.setPaymentId(savedPayment.getPaymentId());
			event.setSenderId(senderId);
			event.setReceiverId(receiver.getId());
			event.setReceiverEmail(receiver.getEmail()); // added email
			event.setAmount(amount);
			event.setStatus("SUCCESS");
			event.setTimestamp(LocalDateTime.now());

			kafkaTemplate.send(PAYMENT_TOPIC, event);
			paymentRepository.save(savedPayment);

			logAudit(senderId, savedPayment.getPaymentId(), amount, "SUCCESS", "Payment successful");

			return savedPayment;

		} catch (FeignException fe) {
			savedPayment.setPaymentStatus(PaymentStatus.FAILED);
			paymentRepository.save(savedPayment);
			logAudit(senderId, savedPayment.getPaymentId(), amount, "FAILED", "Payment failed: " + fe.getMessage());
			throw new PaymentProcessingException("Payment failed due to remote service error");

		} catch (Exception e) {
			savedPayment.setPaymentStatus(PaymentStatus.FAILED);
			paymentRepository.save(savedPayment);
			logAudit(senderId, savedPayment.getPaymentId(), amount, "FAILED", "Payment failed: " + e.getMessage());
			throw new PaymentProcessingException("Payment failed");
		}

	}

	// Helper method for audit logging
	private AuditLogDTO logAudit(Long userId, Long paymentId, Long amount, String status, String remarks) {
		AuditLogDTO auditLog = new AuditLogDTO();
		auditLog.setServiceName("PaymentService");
		auditLog.setAction("PAYMENT_PROCESSED");
		auditLog.setStatus(status);
		auditLog.setReferenceId(paymentId);
		auditLog.setUserId(userId);
		auditLog.setAmount(amount);
		auditLog.setRemarks(remarks);
		auditLog.setCreatedAt(LocalDateTime.now());

		try {
			auditLogClient.log(auditLog);
		} catch (Exception e) {
			// Optionally persist locally if AuditService is down
		}
		return auditLog;

	}

}
