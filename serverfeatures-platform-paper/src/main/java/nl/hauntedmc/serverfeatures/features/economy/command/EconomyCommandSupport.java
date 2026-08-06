package nl.hauntedmc.serverfeatures.features.economy.command;

/** Shared presentation helpers for Economy command adapters. */
final class EconomyCommandSupport {
    private EconomyCommandSupport() {
    }

    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
