package com.siyu.fleet_mgmt_sys.exception;

import com.siyu.fleet_mgmt_sys.dto.LocationConflictResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.NotFoundException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, ex.getMessage()));
    }

    @ExceptionHandler(RobotHasAssignedTasksException.class)
    public ResponseEntity<ErrorResponse> handleConflict(RobotHasAssignedTasksException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, ex.getMessage()));
    }

    @ExceptionHandler(CustomLocationRenameRequiredException.class)
    public ResponseEntity<LocationConflictResponseDTO> handleCustomLocationRenameRequired(
            CustomLocationRenameRequiredException ex
    ) {
        log.warn("Custom location rename confirmation required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new LocationConflictResponseDTO(
                        409,
                        ex.getMessage(),
                        ex.getExistingLocation(),
                        ex.getProposedName()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex); // logs full stack trace
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "An unexpected error occurred"));
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientDisconnect(Exception ex) {
        // Log at WARN or DEBUG level so it doesn't clutter your error logs
        log.warn("Client disconnected before response could be written: {}", ex.getMessage());
    }
}
