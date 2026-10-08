package edu.cit.Abesia.channel;

class TianggeException extends RuntimeException {

    private final int status;
    private final String error;

    TianggeException(int status, String error, String message) {
        super(status + " " + error + ": " + message);
        this.status = status;
        this.error = error;
    }

    int status() { return status; }
    String error() { return error; }

    /** Network trouble, 429 and 5xx are worth retrying; other 4xx are our mistake. */
    boolean retryable() {
        return status == 0 || status == 429 || status >= 500;
    }
}
