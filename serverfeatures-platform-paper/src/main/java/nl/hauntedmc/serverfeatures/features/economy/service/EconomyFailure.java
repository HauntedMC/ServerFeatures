package nl.hauntedmc.serverfeatures.features.economy.service;

/** Common exception unwrapping for asynchronous Economy boundaries. */
final class EconomyFailure {
    private EconomyFailure() {
    }

    static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static String rootMessage(Throwable failure) {
        Throwable current = unwrap(failure);
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    static <T extends Throwable> T find(Throwable failure, Class<T> type) {
        Throwable current = unwrap(failure);
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
