package com.healthee.utils.emailsenderservice;

import com.healthee.utils.exception.ErrorProcessingRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Keep the existing bean name for compatibility with UserService.
// The SMTP provider comes from spring.mail settings, including Brevo.
@Service("mailgunEmailSenderServiceImpl")
public class MailgunEmailSenderServiceImpl implements EmailSenderService {

    private static final Logger log =
            LoggerFactory.getLogger(MailgunEmailSenderServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String configuredSender;
    private final boolean mailEnabled;

    public MailgunEmailSenderServiceImpl(
            JavaMailSender mailSender,
            @Value("${app.mail-from:}") String configuredSender,
            @Value("${app.mail-enabled:false}") boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.configuredSender = configuredSender;
        this.mailEnabled = mailEnabled;
    }

    @Override
    public void sendHtmlEmail(
            String senderEmail, String recipientEmail, String subject, String body
    ) {
        deliver(senderEmail, recipientEmail, subject, body, true);
    }

    @Override
    public void sendEmail(
            String senderEmail, String recipientEmail, String subject, String body
    ) {
        deliver(senderEmail, recipientEmail, subject, body, false);
    }

    @Override
    public void sendAccountActivationEmail(
            String emailAddress, String id, String fullName, String otp
    ) {
        sendCodeEmail(emailAddress, fullName, otp,
                "Verify your Healthee account",
                "Enter this code in the app to verify your email address.");
    }

    @Override
    public void sendPasswordChangeEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        sendCodeEmail(emailAddress, fullName, otp,
                "Confirm your Healthee password change",
                "Enter this code in the app to confirm your password change.");
    }

    @Override
    public void sendPasswordResetEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        sendCodeEmail(emailAddress, fullName, otp,
                "Reset your Healthee password",
                "Enter this code in the app to reset your password.");
    }

    @Override
    public void sendBuyerPasswordResetEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        sendPasswordResetEmail(emailAddress, userId, otp, fullName);
    }

    @Override
    public void sendAdminPasswordResetEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        sendCodeEmail(emailAddress, fullName, otp,
                "Reset your Healthee admin password",
                "Enter this code in the admin portal to reset your password.");
    }

    @Override
    public void sendAccountSetupCompletionEmail(String emailAddress, String fullName) {
        String body = emailBody(fullName, "Your account is ready",
                "Your Healthee account setup is complete. You can now sign in.", null);
        sendHtmlEmail(configuredSender, emailAddress, "Welcome to Healthee", body);
    }

    @Override
    public void sendAdminAccountActivationEmail(
            String emailAddress, String fullName, String password, String otp
    ) {
        // Preserve the interface; passwords must never be included in emails.
        sendCodeEmail(emailAddress, fullName, otp,
                "Verify your Healthee admin account",
                "Enter this code in the admin portal to verify your email address.");
    }

    @Override
    public void sendDirectorAccountActivationEmail(
            String emailAddress, String id, String otp
    ) {
        sendAccountActivationEmail(emailAddress, id, null, otp);
    }

    private void sendCodeEmail(
            String recipient, String fullName, String otp, String title, String instruction
    ) {
        if (isBlank(otp)) {
            throw new ErrorProcessingRequestException("Email verification code is missing");
        }
        sendHtmlEmail(configuredSender, recipient, title,
                emailBody(fullName, title, instruction, otp));
    }

    private void deliver(
            String sender, String recipient, String subject, String body, boolean html
    ) {
        if (!mailEnabled) {
            throw new ErrorProcessingRequestException(
                    "Email sending is disabled. Configure and enable the email service.");
        }

        String from = isBlank(sender) ? configuredSender : sender;
        if (isBlank(from) || isBlank(recipient) || isBlank(subject) || body == null
                || hasLineBreak(from) || hasLineBreak(recipient) || hasLineBreak(subject)) {
            throw new ErrorProcessingRequestException("Invalid email configuration or message");
        }

        try {
            InternetAddress fromAddress = new InternetAddress(from.trim(), true);
            InternetAddress toAddress = new InternetAddress(recipient.trim(), true);
            fromAddress.validate();
            toAddress.validate();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, html);
            mailSender.send(message);
        } catch (MessagingException | MailException | IllegalArgumentException exception) {
            // Do not log message bodies, OTPs, passwords or SMTP credentials.
            log.warn("Email delivery failed: {}", exception.getClass().getSimpleName());
            throw new ErrorProcessingRequestException(
                    "Unable to send email. Please try again later.");
        }
    }

    private String emailBody(String fullName, String title, String instruction, String otp) {
        String name = isBlank(fullName) ? "there" : fullName.trim();
        String code = otp == null ? "" :
                "<p style='padding:16px;background:#edf8ef;color:#176534;"
                        + "font-size:28px;font-weight:bold;letter-spacing:4px;'>"
                        + escape(otp) + "</p>"
                        + "<p>Use this code before it expires. Do not share it with anyone.</p>"
                        + "<p>If you did not request this, you can ignore this email.</p>";

        return "<!doctype html><html lang='en'><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:24px;background:#f5f7f5;"
                + "font-family:Arial,sans-serif;color:#25352b;'>"
                + "<div style='max-width:560px;margin:auto;padding:28px;"
                + "background:#ffffff;border-radius:12px;'>"
                + "<p style='color:#176534;font-size:22px;font-weight:bold;'>HEALTHEE</p>"
                + "<h2>" + escape(title) + "</h2>"
                + "<p>Hello " + escape(name) + ",</p>"
                + "<p>" + escape(instruction) + "</p>"
                + code + "<p>The Healthee team</p></div></body></html>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}