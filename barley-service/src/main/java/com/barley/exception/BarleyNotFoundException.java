package com.barley.exception;

public class BarleyNotFoundException extends RuntimeException {

    private final Long id;

    public BarleyNotFoundException(Long id) {
        super("Barley inventory item not found with id: " + id);
        this.id = id;
    }

    public BarleyNotFoundException(String message) {
        super(message);
        this.id = null;
    }

    public Long getId() {
        return id;
    }
}
