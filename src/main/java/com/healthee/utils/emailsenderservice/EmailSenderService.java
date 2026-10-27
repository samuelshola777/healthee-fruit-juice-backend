package com.healthee.utils.emailsenderservice;


public interface EmailSenderService {


    void sendHtmlEmail(String senderEmail, String recipientEmail,String subject, String body);
    void sendEmail(String senderEmail, String recipientEmail, String subject, String body);
    void sendAccountActivationEmail(String emailAddress, String id, String fullName, String otp);
    void sendPasswordChangeEmail(String emailAddress, String userId, String otp, String fullName);
    void sendPasswordResetEmail(String emailAddress, String userId, String otp, String fullName);
    void sendBuyerPasswordResetEmail(String emailAddress, String userId, String otp, String fullName);
    void sendAdminPasswordResetEmail(String emailAddress, String userId, String otp, String fullName);
    void sendAccountSetupCompletionEmail(String emailAddress, String s);
    void sendAdminAccountActivationEmail(String emailAddress, String string, String password, String string2);
    void sendDirectorAccountActivationEmail(String emailAddress, String id, String string);



}
