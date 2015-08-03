package com.joelinna.exception;

/**
 * Created by Threepwood on 8/2/2015.
 */
public class RestServerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RestServerException(String message, Throwable t) {
        super(message, t);
    }

    public RestServerException(String message) {
        super(message);
    }


    public RestServerException(Throwable t) {
        super(t);
    }

}
