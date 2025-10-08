package nl.hauntedmc.serverfeatures.features.chatlayout.internal.util;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

public class MiniMessageFormatter {

    public static final TagResolver urls = TagResolver.resolver("url", (args, context) -> {
        final String url = args.popOr("version expected").value();
        return Tag.styling(ClickEvent.openUrl(url));
    });
    public static final MiniMessage chatSerializer = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(urls)
                    .build()
            )
            .build();

    public static final MiniMessage chatColorSerializer = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(urls)
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.gradient())
                    .build()
            )
            .build();

    public static final MiniMessage  chatColorExtraSerializer = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(urls)
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.gradient())
                    .build()
            )
            .build();

    public static final MiniMessage chatColorAllSerializer = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(urls)
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.clickEvent())
                    .resolver(StandardTags.hoverEvent())
                    .build()
            )
            .build();

    public static final MiniMessage prefixSerializer = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.clickEvent())
                    .resolver(StandardTags.hoverEvent())
                    .build()
            )
            .build();
}
