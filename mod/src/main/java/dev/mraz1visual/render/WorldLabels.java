package dev.mraz1visual.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bake label glyphs on ticks, before RenderPearl starts a world render pass. */
final class WorldLabels {
    static final class Entry {
        final Component text;
        int width;
        boolean ready;
        Entry(Component text) { this.text=text; }
    }
    private static final Map<Component,Entry> CACHE=new LinkedHashMap<>(128,.75f,true);
    private static final ArrayDeque<Entry> PENDING=new ArrayDeque<>();
    private WorldLabels() {}
    static void register() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public net.minecraft.resources.Identifier getFabricId() {
                        return net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual","world_labels");
                    }
                    @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        net.minecraft.client.Minecraft.getInstance().execute(() -> { CACHE.clear(); PENDING.clear(); });
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if (mc.getOverlay() != null) return;
            while(!PENDING.isEmpty()) {
                Entry entry=PENDING.removeFirst();
                mc.font.prepareText(entry.text.getVisualOrderText(),0,0,-1,false,false,0);
                entry.width=mc.font.width(entry.text);
                entry.ready=true;
            }
        });
    }
    static Entry get(Component component) {
        // An explicit font keeps GUI font remapping from changing world labels
        // while Mod Menu is open over the world (including during pre-baking).
        Component key=component.copy().withStyle(style->style.withFont(
                new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual",
                        dev.mraz1visual.core.Modules.client.vanillaFont.get() ? "world_native" : "integration_native"))));
        Entry cached=CACHE.get(key);
        if(cached!=null)return cached;
        if(CACHE.size()>=512)CACHE.remove(CACHE.keySet().iterator().next());
        Entry entry=new Entry(key.copy());
        CACHE.put(entry.text,entry);
        PENDING.addLast(entry);
        return entry;
    }
}
