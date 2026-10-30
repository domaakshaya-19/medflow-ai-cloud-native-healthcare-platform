package com.medflow.auth.exception;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException { private final HttpStatus status; public ApiException(HttpStatus s,String m){super(m);status=s;} public HttpStatus getStatus(){return status;} }
