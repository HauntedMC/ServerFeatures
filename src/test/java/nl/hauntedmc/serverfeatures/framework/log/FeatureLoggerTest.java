package nl.hauntedmc.serverfeatures.framework.log;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureLoggerTest {

    @Test
    void prefixesEveryLogMessageWithFeatureName() {
        Logger logger = Logger.getLogger("feature-logger-test-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        FeatureLogger featureLogger = new FeatureLogger(logger, "ChatFilter");
        featureLogger.info("info");
        featureLogger.warning("warn");
        featureLogger.severe("error");
        featureLogger.fine("fine");
        featureLogger.log(Level.CONFIG, "config");

        assertEquals("[ChatFilter] info", records.get(0).getMessage());
        assertEquals("[ChatFilter] warn", records.get(1).getMessage());
        assertEquals("[ChatFilter] error", records.get(2).getMessage());
        assertEquals("[ChatFilter] fine", records.get(3).getMessage());
        assertEquals("[ChatFilter] config", records.get(4).getMessage());
    }
}
