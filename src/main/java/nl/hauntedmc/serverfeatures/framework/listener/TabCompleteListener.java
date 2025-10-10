package nl.hauntedmc.serverfeatures.framework.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import nl.hauntedmc.serverfeatures.api.command.tab.TabService;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global async tab completion listener (Paper/Folia).
 * Delegates to TabService registry.
 */
public final class TabCompleteListener implements Listener {

    private final TabService service;

    public TabCompleteListener(TabService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncTab(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) return;

        // Parse command
        final String buffer = event.getBuffer();
        final String noSlash = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        final String[] split = noSlash.split(" ", -1);
        if (split.length == 0) return;

        // Find tree
        final String label = split[0].toLowerCase(Locale.ROOT);
        final TabTree tree = service.find(label).orElse(null);
        if (tree == null) return;

        // Prepare request
        final String[] args = new String[Math.max(0, split.length - 1)];
        System.arraycopy(split, 1, args, 0, args.length);

        // Fulfill completions
        final TabRequest req = new TabRequest(event.getSender(), label, args, new MatchState(), service);
        final List<Suggestion> sugs = tree.complete(req);

        // Convert to Paper completions
        final List<AsyncTabCompleteEvent.Completion> out = new ArrayList<>(sugs.size());
        for (Suggestion s : sugs) {
            final String text = (s.insert() != null) ? s.insert() : s.text();
            out.add(AsyncTabCompleteEvent.Completion.completion(text, s.tooltip()));
        }

        event.completions(out);
        event.setHandled(true);
    }
}
