package nl.hauntedmc.serverfeatures.api.command.meta;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record CommandMeta(
        @NotNull String name,
        @Nullable String description,
        @Nullable String usage,
        @Nullable List<String> aliases,
        @Nullable String permission
) {
    /**
     * Builder for {@link CommandMeta} with sensible defaults.
     */
    public static final class Builder {
        private final String name;
        private String description;
        private String usage;
        private List<String> aliases;
        private String permission;

        public Builder(@NotNull String name) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Command name must be provided and non-blank.");
            }
            this.name = name;
        }

        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }

        /**
         * Usage may be omitted or blank; FeatureCommand will fall back to "/"+name.
         */
        public Builder usage(@Nullable String usage) {
            this.usage = usage;
            return this;
        }

        public Builder aliases(@Nullable List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder permission(@NotNull String permission) {
            this.permission = permission;
            return this;
        }

        public CommandMeta build() {
            return new CommandMeta(name, description, usage, aliases, permission);
        }
    }
}
