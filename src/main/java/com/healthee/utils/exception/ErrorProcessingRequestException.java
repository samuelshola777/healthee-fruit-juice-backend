package com.healthee.utils.exception;

public class ErrorProcessingRequestException extends RuntimeException{

    public ErrorProcessingRequestException(String message){
        super(message);
    }
}
