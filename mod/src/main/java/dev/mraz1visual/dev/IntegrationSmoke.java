package dev.mraz1visual.dev;

import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.integration.IntegrationScreens;
import dev.mraz1visual.models.ModelRegistry;
import dev.mraz1visual.ui.ModelGallery;
import dev.mraz1visual.ui.menu.Mraz1VisualTitleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EntityType;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in development smoke test in an isolated game directory, never signs in. */
public final class IntegrationSmoke {
    private static int step, ticks;
    private static Screen title;
    private static java.util.concurrent.CompletableFuture<Void> switchResult;
    private static long worldDeadline;
    private static int vanillaFontWidth;
    private static boolean lightMods, lightAccounts, compactTitle;
    private static int clientPhase, fullscreenPhase;
    private static int settingsPhase, originalScale, wardrobeFrame;
    private static java.net.ServerSocket connectionFixture;
    private static long connectionDeadline;
    private static java.util.concurrent.CompletableFuture<Void> resourceReload;
    private static String fullscreenLabel;
    private static Screen fullscreenScreen;
    private static final java.util.Set<String> fullscreenDone=new java.util.HashSet<>();
    private static final StringBuilder report = new StringBuilder();
    private static final String VERSION = net.minecraft.SharedConstants.getCurrentVersion().id();
    private IntegrationSmoke() {}
    public static void init() {
        if (!Boolean.getBoolean("mraz1.uiSmoke") || !FabricLoader.getInstance().isDevelopmentEnvironment()&&!Boolean.getBoolean("mraz1.productionSmoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(IntegrationSmoke::tick);
    }
    private static void check(boolean result, String message) {
        report.append(result ? "PASS " : "FAIL ").append(message).append('\n');
    }
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + "-" + VERSION + ".png", mc.getMainRenderTarget(), 1, ignored -> {});
    }
    private static void tick(Minecraft mc) {
        if(step==19)EclipseSmoke.frame(mc);
        if (step < 0 || mc.getOverlay() != null || (mc.screen == null && step < 11) || ++ticks < (step==20?1:step == 13 ? 8 : 60)) return;
        ticks = 0;
        try {
            switch (step++) {
                case 0 -> {
                    title = new Mraz1VisualTitleScreen(); mc.setScreen(title); ModelRegistry.ensureLoaded();
                    check(mc.screen instanceof Mraz1VisualTitleScreen,"original Mraz1Visual title screen is preserved");
                    check(dev.mraz1visual.core.Modules.client.settings().stream().noneMatch(s->s.id.equals("visual_style")||s.id.equals("aura_media")),"no Aura shell or wallpaper selectors in client settings");
                    check(dev.mraz1visual.wallpaper.WallpaperPlayer.current()==null,"legacy imported Aura wallpaper is not active");
                    if (Boolean.getBoolean("mraz1.verifySavedPulse")) {
                        check("pulse:wings_124".equals(dev.mraz1visual.models.ModelPrefs.wing)
                                && "pulse:hat_98".equals(dev.mraz1visual.models.ModelPrefs.hat)
                                && "pulse:bodywear_137".equals(dev.mraz1visual.models.ModelPrefs.back), "wardrobe survives full process restart");
                    }
                    verifySettingsPersistence();
                    dev.mraz1visual.core.Modules.client.vanillaFont.set(true);
                    vanillaFontWidth = mc.font.width("Wim Москва 0123456789");
                    dev.mraz1visual.core.Modules.client.vanillaFont.set(false);
                    int nativeWidth = mc.font.width(integrationText());
                    check(nativeWidth != vanillaFontWidth && nativeWidth == mc.font.width("Wim Москва 0123456789"), "native integration uses original client font metrics (" + nativeWidth + "/" + vanillaFontWidth + ")");
                }
                case 1 -> {
                    if (settingsPhase < 7) {
                        switch (settingsPhase++) {
                            case 0 -> {
                                originalScale = mc.options.guiScale().get();
                                mc.options.guiScale().set(2);
                                dev.mraz1visual.compat.Mc.resizeGui(mc);
                                mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(title, mc.options));
                            }
                            case 1 -> {
                                check(IntegrationScreens.themed(mc.screen), "nested key bindings screen uses client theme");
                                shot(mc, "pulse-controls-scale2");
                                mc.options.guiScale().set(3);
                                dev.mraz1visual.compat.Mc.resizeGui(mc);
                            }
                            case 2 -> { shot(mc,"pulse-controls-scale3"); resourceReload = mc.reloadResourcePacks(); }
                            case 3 -> {
                                if (!resourceReload.isDone()) { settingsPhase--; break; }
                                resourceReload.join();
                                check(mc.font.width(integrationText()) == mc.font.width("Wim Москва 0123456789"), "font metrics survive resource reload");
                                mc.options.guiScale().set(originalScale);
                                dev.mraz1visual.compat.Mc.resizeGui(mc);
                                mc.screen.onClose();
                                check(mc.screen == title, "key bindings return to the original title");
                                // A local listener lets us observe connecting and disconnection without an external server.
                                connectionFixture = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
                                String address = "127.0.0.1:" + connectionFixture.getLocalPort();
                                var server = new net.minecraft.client.multiplayer.ServerData("Local UI fixture", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(title, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), server, false, null);
                                connectionDeadline = System.nanoTime() + 240_000_000_000L;
                            }
                            case 4 -> {
                                check(mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen && IntegrationScreens.themed(mc.screen), "connecting screen retains native controls");
                                shot(mc, "pulse-connecting");
                                connectionFixture.close();
                            }
                            case 5 -> {
                                if (!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) && System.nanoTime() < connectionDeadline) { settingsPhase--; break; }
                                if (!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) {
                                    throw new IllegalStateException("Local connection has not finished; cannot continue to the world fixture");
                                }
                                check(IntegrationScreens.themed(mc.screen), "actual disconnection reason visible on themed screen");
                                shot(mc,"pulse-disconnected");
                                mc.setScreen(title);
                            }
                            case 6 -> {
                                check(mc.screen == title, "title available after local network fixture");
                            }
                        }
                        step--; return;
                    }
                    if(!fullscreenRoundTrip(mc,"title")){step--;return;} verifyTitleLayout(); shot(mc,"mraz-title-1600"); mc.getWindow().setWindowed(854,480); }
                case 2 -> {
                    verifyTitleLayout();
                    if(!compactTitle){shot(mc,"mraz-title-854");mc.getWindow().setWindowed(1440,810);compactTitle=true;step--;return;}
                    shot(mc,"mraz-title-1440");mc.getWindow().setWindowed(1920,1080);
                }
                case 3 -> {
                    verifyTitleLayout();
                    shot(mc,"mraz-title-1920");
                    IntegrationScreens.mods(title);
                }
                case 4 -> {
                    if(!fullscreenRoundTrip(mc,"Mod Menu")){step--;return;}
                    check(mc.screen.getClass().getName().equals("com.terraformersmc.modmenu.gui.ModsScreen"), "real Mod Menu opens from original title");
                    check(mc.font.width("Wim Москва 0123456789") == mc.font.width(integrationText()) && mc.font.width(integrationText()) != vanillaFontWidth,
                            "native integration text uses Mraz1Visual font");
                    shot(mc,"modmenu-1920");
                    for (var child : mc.screen.children()) if (child instanceof EditBox box) { box.setValue("mraz"); break; }
                }
                case 5 -> {
                    if(!lightMods) {
                        shot(mc,"modmenu-search");
                        dev.mraz1visual.core.Modules.client.theme.set(1); lightMods=true;step--;return;
                    }
                    shot(mc,"modmenu-light");
                    check(dev.mraz1visual.integration.IntegrationTheme.nativeTextColor(0xFFFFFFFF)==0xFF111114 && !dev.mraz1visual.ui.Theme.isLight(),"Mod Menu light theme uses client colors without leaking global UI state");
                    dev.mraz1visual.core.Modules.client.vanillaFont.set(true);
                    check(mc.font.width("Wim Москва 0123456789")==vanillaFontWidth,"native integrations respect the client's Minecraft font option");
                    dev.mraz1visual.core.Modules.client.vanillaFont.set(false);
                    dev.mraz1visual.core.Modules.client.theme.set(0);
                    Screen modMenu = mc.screen;
                    var entrypoint = FabricLoader.getInstance().getEntrypoints("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class)
                            .stream().filter(api -> api instanceof dev.mraz1visual.integration.MrazModMenu).findFirst();
                    check(entrypoint.isPresent(), "Mraz1Visual Mod Menu configuration entrypoint is registered");
                    if (entrypoint.isPresent()) {
                        Screen config = entrypoint.get().getModConfigScreenFactory().create(modMenu);
                        mc.setScreen(config);
                        check(mc.screen instanceof dev.mraz1visual.ui.ClientScreen, "Mod Menu configuration opens real client settings");
                        mc.screen.onClose();
                        check(mc.screen == modMenu, "client settings return to Mod Menu");
                    }
                    mc.screen.onClose(); check(mc.screen == title,"Mod Menu returns to parent");
                    // Only suppress the first-run disclaimer in memory, in this isolated development run.
                    Class.forName("ru.vidtu.ias.config.IASStorage").getField("gameDisclaimerShown").setBoolean(null,true);
                    IntegrationScreens.accounts(title);
                }
                case 6 -> {
                    if(!fullscreenRoundTrip(mc,"IAS")){step--;return;}
                    check(mc.screen.getClass().getName().equals("ru.vidtu.ias.screen.AccountScreen"),"real IAS accounts screen opens");
                    shot(mc,"ias-accounts-1920"); mc.getWindow().setWindowed(854,480);
                }
                case 7 -> {
                    if(!lightAccounts){shot(mc,"ias-accounts-854");dev.mraz1visual.core.Modules.client.theme.set(1);lightAccounts=true;step--;return;}
                    shot(mc,"ias-accounts-light-854");
                    check(!dev.mraz1visual.ui.Theme.isLight(),"IAS light theme does not modify the rest of the client UI");
                    dev.mraz1visual.core.Modules.client.theme.set(0);
                    mc.screen.onClose(); check(mc.screen == title,"IAS returns to parent"); ModelGallery.open(ModelGallery.Tab.COSMETICS);
                }
                case 8 -> {
                    if(!fullscreenRoundTrip(mc,"model gallery")){step--;return;}
                    if (ModelRegistry.loading()) { step--; return; }
                    long count = ModelRegistry.all().stream().filter(e->e.collection.equals("aura")).count();
                    long broken = ModelRegistry.all().stream().filter(e->e.collection.equals("aura") && e.broken()).count();
                    check(count == 78,"all 78 Aura cosmetics loaded: " + count);
                    check(broken == 0,"Aura cosmetic errors: " + broken);
                    var pulse = ModelRegistry.all().stream().filter(e -> e.collection.equals("pulse")).toList();
                    check(pulse.size() == 37 && pulse.stream().allMatch(e -> !e.broken() && e.mesh() != null && e.auraPlacement != null), "all 37 Pulse attachments have geometry, texture and placement");
                    check(pulse.stream().filter(e -> e.animatedTextures()).count() == 4, "all four Pulse texture strips animate");
                    verifyCosmeticPersistence();
                    verifyWardrobe();
                    long auraItems=ModelRegistry.all().stream().filter(e->e.collection.equals("aura-items")).count();
                    long brokenItems=ModelRegistry.all().stream().filter(e->e.collection.equals("aura-items")&&e.broken()).count();
                    check(auraItems>0 && brokenItems==0,"Aura item models loaded: " + auraItems + ", errors: " + brokenItems);
                    for(var item:ModelRegistry.all())if(item.collection.equals("aura-items")&&item.broken())report.append(item.id).append(": ").append(item.error).append('\n');
                    check(ModelRegistry.all().stream().filter(e->e.collection.equals("aura")).allMatch(e->e.auraPlacement!=null && e.mesh()!=null && e.mesh().rootTransform!=null && e.mesh().rootTransform.isFinite()
                            && java.util.stream.IntStream.range(0,6).allMatch(i->Float.isFinite(e.mesh().bounds[i]))), "all Aura source orientations and attachment placements loaded");
                    check(ModelRegistry.all().stream().filter(e->e.collection.equals("aura")).noneMatch(e->e.warnings.stream().anyMatch(w->w.contains("анимация пропущена"))),"all converted animations parse");
                    for (var e : ModelRegistry.all()) if (e.collection.equals("aura") && e.broken()) report.append(e.id).append(": ").append(e.error).append('\n');
                    shot(mc,"aura-cosmetics-854"); mc.getWindow().setWindowed(1600,900);
                }
                case 9 -> {
                    if(clientPhase==0){shot(mc,"aura-cosmetics-1600");var client=new dev.mraz1visual.ui.ClientScreen();mc.setScreen(client);client.harnessReveal("auto_sprint");clientPhase=1;step--;return;}
                    if(clientPhase==1){
                        if(!fullscreenRoundTrip(mc,"ClickGUI")){step--;return;}
                        var header=dev.mraz1visual.ui.ClientScreen.class.getDeclaredMethod("bannerH");header.setAccessible(true);
                        check(((Integer)header.invoke(mc.screen))==64,"demo preview removed; compact header leaves room for settings");
                        shot(mc,"mraz-settings-without-demo");((dev.mraz1visual.ui.ClientScreen)mc.screen).harnessTab("COSMETICS");clientPhase=2;step--;return;
                    }
                    if(!fullscreenRoundTrip(mc,"cosmetics tab")){step--;return;}
                    shot(mc,"mraz-aura-wardrobe");
                    switchResult = ru.vidtu.ias.IASMinecraft.account(mc, new ru.vidtu.ias.auth.LoginData("MrazUiSmoke", java.util.UUID.nameUUIDFromBytes("OfflinePlayer:MrazUiSmoke".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ias:offline", false));
                }
                case 10 -> {
                    if (!switchResult.isDone()) { step--; return; }
                    check(!switchResult.isCompletedExceptionally() && mc.getUser().getName().equals("MrazUiSmoke"), "IAS changes the actual offline Minecraft session");
                    if (Boolean.getBoolean("mraz1.worldSmoke")) { worldDeadline = System.nanoTime()+120_000_000_000L; Harness.openWorld(mc); }
                    else finish(mc);
                }
                case 11 -> {
                    if (mc.level == null || mc.player == null) {
                        if (System.nanoTime()>worldDeadline) { check(false,"isolated world opens before timeout");finish(mc); }
                        else step--;
                        return;
                    }
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    dev.mraz1visual.models.ModelPrefs.hat="pulse:hat_98";
                    dev.mraz1visual.models.ModelPrefs.wing="pulse:wings_124";
                    dev.mraz1visual.models.ModelPrefs.back="pulse:bodywear_137";
                    for (var e : ModelRegistry.all()) if (e.collection.equals("aura")) {
                        if(e.kind==dev.mraz1visual.models.ModelEntry.Kind.WING && dev.mraz1visual.models.ModelPrefs.wing.isEmpty())dev.mraz1visual.models.ModelPrefs.wing=e.id;
                        if(e.kind==dev.mraz1visual.models.ModelEntry.Kind.HAT && dev.mraz1visual.models.ModelPrefs.hat.isEmpty())dev.mraz1visual.models.ModelPrefs.hat=e.id;
                        if(e.kind==dev.mraz1visual.models.ModelEntry.Kind.BACK && dev.mraz1visual.models.ModelPrefs.back.isEmpty())dev.mraz1visual.models.ModelPrefs.back=e.id;
                        if(e.kind==dev.mraz1visual.models.ModelEntry.Kind.PET && dev.mraz1visual.models.ModelPrefs.pet.isEmpty())dev.mraz1visual.models.ModelPrefs.pet=e.id;
                    }
                    for(String id:new String[]{"model_cosmetics","model_pets","meteor_fly","aura_wave","speech_bubbles","armor_notifier","hit_bubbles","jump_circle","armor_hud",
                            "frame_esp","aura_cubes","world_lines","effect_remind","armor_durability_tag","potion_highlight","see_invisible","no_fluids","aura_particles","motion_blur","hotbar"}){
                        var module=dev.mraz1visual.Mraz1Visual.MODULES.find(id);
                        if(module==null)throw new IllegalStateException("Missing visual module: "+id);
                        module.setEnabled(true);
                    }
                    // Capture the attachments alone before the HUD and particle fixtures cover the body.
                    if (wardrobeFrame++ < 2) {
                        if (wardrobeFrame == 2) {
                            shot(mc, "pulse-ghoul-halo-katana");
                            mc.options.keyShift.setDown(true);
                            mc.player.setYRot(mc.player.getYRot()+30);
                        }
                        step--; return;
                    }
                    shot(mc, "pulse-attachments-sneak");
                    mc.options.keyShift.setDown(false);
                    var helmet=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
                    helmet.setDamageValue(helmet.getMaxDamage()-1);
                    mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
                    initializeVisualFixtures(mc,helmet);
                    check(true,"isolated world and native player cosmetics initialized");
                }
                case 12 -> {
                    mc.setScreen(null);
                    var particles=dev.mraz1visual.Mraz1Visual.MODULES.find("aura_particles");
                    var shape=(dev.mraz1visual.setting.EnumSetting)particles.settings().stream().filter(s->s.id.equals("shape")).findFirst().orElseThrow();
                    shape.set(10);((dev.mraz1visual.core.AttackListener)particles).onAttack(mc.player);
                    shape.set(11);particles.onTick(mc);check(((dev.mraz1visual.module.AuraParticles)particles).customLoaded(),"custom PNG particle file is loaded by the native texture manager");
                    ((dev.mraz1visual.core.AttackListener)particles).onAttack(mc.player);dev.mraz1visual.module.AuraParticles.totem(mc.player);
                    dev.mraz1visual.module.SpeechBubbles.message(net.minecraft.network.chat.Component.literal("<MrazUiSmoke> AuraVisual integration"));
                    for(String id:new String[]{"hit_bubbles","aura_wave"}) {
                        var module=dev.mraz1visual.Mraz1Visual.MODULES.find(id);
                        if(module instanceof dev.mraz1visual.core.AttackListener listener)listener.onAttack(mc.player);
                    }
                    mc.player.setDeltaMovement(0,.42,0);
                    mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,80));
                }
                case 13 -> {
                    check(dev.mraz1visual.render.WorldDraw.errorCount()==0,"no world renderer errors");
                    check(dev.mraz1visual.render.HudDraw.errorCount()==0,"no HUD renderer errors");
                    shot(mc,"aura-world-cosmetics");
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                }
                case 14 -> {
                    if(!fullscreenRoundTrip(mc,"inventory")){step--;return;}
                    shot(mc,"mraz-potion-highlights");
                    check(dev.mraz1visual.render.HudDraw.errorCount()==0,"potion highlights and effect reminders render without errors");
                    mc.screen.onClose();
                    if (VERSION.equals("26.3")) {
                        dev.mraz1visual.core.Modules.client.theme.set(1);
                        dev.mraz1visual.module.SpeechBubbles.message(net.minecraft.network.chat.Component.literal("<MrazUiSmoke> Мир: Жёлтый ёж №42"));
                        IntegrationScreens.mods(null);
                    } else {step=17;prepareVanillaHud(mc);}
                }
                case 15 -> {
                    check(dev.mraz1visual.render.WorldDraw.errorCount()==0,"world labels remain safe with Mod Menu over the world in Mraz style");
                    mc.screen.onClose();
                }
                case 16 -> {
                    check(mc.screen==null && dev.mraz1visual.render.WorldDraw.errorCount()==0,"return from Mod Menu to the world preserves label rendering");
                    dev.mraz1visual.core.Modules.client.theme.set(0);
                    prepareVanillaHud(mc);
                }
                case 17 -> {
                    check(dev.mraz1visual.render.post.WorldMotionBlur.frames()>0&&dev.mraz1visual.render.post.WorldMotionBlur.errors()==0,"motion blur executes before HUD without GPU errors");
                    check(!(dev.mraz1visual.Mraz1Visual.MODULES.find("motion_blur") instanceof dev.mraz1visual.core.HudLayer),"motion blur never covers the vanilla/custom hotbar or player status HUD");
                    shot(mc,"mraz-vanilla-hotbar-blur");dev.mraz1visual.Mraz1Visual.MODULES.find("hotbar").setEnabled(true);
                    mc.player.setYRot(mc.player.getYRot()+15);
                }
                case 18 -> {
                    shot(mc,"mraz-custom-hotbar-blur");
                    check(dev.mraz1visual.render.post.WorldMotionBlur.errors()==0&&dev.mraz1visual.render.HudDraw.errorCount()==0,"custom hotbar and motion blur render together without errors");
                    if(mc.isWindowActive())check(mc.mouseHandler.isMouseGrabbed(),"return from UI restores camera mouse capture");
                    step=19;
                }
                case 19 -> {if(!EclipseSmoke.run(mc,IntegrationSmoke::check))step=19;else step=20;}
                case 20 -> {
                    if (!VisualModuleAudit.tick(mc,IntegrationSmoke::check)) { step=20; return; }
                    if (Boolean.getBoolean("mraz1.productionSmoke")) {
                        dev.mraz1visual.models.ModelPrefs.hat="pulse:hat_98";
                        dev.mraz1visual.models.ModelPrefs.wing="pulse:wings_124";
                        dev.mraz1visual.models.ModelPrefs.back="pulse:bodywear_137";
                        dev.mraz1visual.models.ModelPrefs.save();
                    }
                    finish(mc);
                }
            }
        } catch (Throwable e) {
            report.append("FAIL ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            ModuleManager.LOG.error("UI smoke failed ({})", e.getClass().getSimpleName());
            finish(mc);
        }
    }
    private static boolean fullscreenRoundTrip(Minecraft mc,String label){
        if(fullscreenDone.contains(label))return true;
        if(!label.equals(fullscreenLabel)){fullscreenLabel=label;fullscreenPhase=0;fullscreenScreen=mc.screen;}
        if(fullscreenPhase==0){fullscreen(mc,true);fullscreenPhase=1;return false;}
        boolean normal;
        //? if >=26.3 {
        /*normal=org.lwjgl.sdl.SDLMouse.SDL_CursorVisible()&&!org.lwjgl.sdl.SDLMouse.SDL_GetWindowRelativeMouseMode(mc.getWindow().handle());
        *///?} else {
        normal=org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().handle(),com.mojang.blaze3d.platform.InputConstants.CURSOR)==com.mojang.blaze3d.platform.InputConstants.CURSOR_NORMAL;
        //?}
        check(mc.screen==fullscreenScreen&&!mc.mouseHandler.isMouseGrabbed()&&normal,label+" native cursor visible and released after "+(fullscreenPhase==1?"fullscreen":"windowed")+" transition");
        check(mc.screen.width==mc.getWindow().getGuiScaledWidth()&&mc.screen.height==mc.getWindow().getGuiScaledHeight(),label+" input dimensions match resized GUI");
        if(fullscreenPhase==1){fullscreen(mc,false);fullscreenPhase=2;return false;}
        fullscreenDone.add(label);return true;
    }
    private static void prepareVanillaHud(Minecraft mc){
        dev.mraz1visual.Mraz1Visual.MODULES.find("hotbar").setEnabled(false);
        dev.mraz1visual.Mraz1Visual.MODULES.find("health_bars").setEnabled(false);
        mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
        mc.player.setHealth(14);mc.player.getFoodData().setFoodLevel(16);mc.player.experienceLevel=4;mc.player.experienceProgress=.55f;
    }
    private static void fullscreen(Minecraft mc,boolean full){
        mc.options.fullscreen().set(full);
        //? if >=26.3 {
        /*mc.getWindow().setFullscreen(full);
        *///?} else {
        if(mc.getWindow().isFullscreen()!=full)mc.getWindow().toggleFullScreen();
        //?}
    }
    private static void verifyWardrobe(){
        var catalog=dev.mraz1visual.ui.WardrobePanel.catalog();
        var kinds=new dev.mraz1visual.models.ModelEntry.Kind[]{dev.mraz1visual.models.ModelEntry.Kind.WING,dev.mraz1visual.models.ModelEntry.Kind.HAT,dev.mraz1visual.models.ModelEntry.Kind.BACK,dev.mraz1visual.models.ModelEntry.Kind.PET};
        int[] counts={31,26,35,23};
        String hat=dev.mraz1visual.models.ModelPrefs.hat,wing=dev.mraz1visual.models.ModelPrefs.wing,back=dev.mraz1visual.models.ModelPrefs.back,pet=dev.mraz1visual.models.ModelPrefs.pet;
        dev.mraz1visual.ui.WardrobePanel.clear();
        for(int i=0;i<kinds.length;i++){
            var kind=kinds[i];check(catalog.stream().filter(e->e.kind==kind).count()==counts[i],"Built-in wardrobe category "+kind+" has "+counts[i]+" real models");
            var item=catalog.stream().filter(e->e.kind==kind && (kind==dev.mraz1visual.models.ModelEntry.Kind.PET || e.collection.equals("pulse"))).findFirst().orElseThrow();
            dev.mraz1visual.ui.WardrobePanel.toggle(item);dev.mraz1visual.models.ModelPrefs.load();
            check(dev.mraz1visual.ui.WardrobePanel.selected(kind).equals(item.id),"wardrobe selection persists for "+kind);
            dev.mraz1visual.ui.WardrobePanel.toggle(item);dev.mraz1visual.models.ModelPrefs.load();
            check(dev.mraz1visual.ui.WardrobePanel.selected(kind).isEmpty(),"wardrobe item can be removed and remains removed after reload: "+kind);
        }
        check(dev.mraz1visual.module.ModelCosmetics.pick(dev.mraz1visual.models.ModelEntry.Kind.HAT)==null&&dev.mraz1visual.module.ModelCosmetics.pick(dev.mraz1visual.models.ModelEntry.Kind.WING)==null,"empty cosmetic selection does not silently equip a different model");
        dev.mraz1visual.models.ModelPrefs.hat=hat;dev.mraz1visual.models.ModelPrefs.wing=wing;dev.mraz1visual.models.ModelPrefs.back=back;dev.mraz1visual.models.ModelPrefs.pet=pet;dev.mraz1visual.models.ModelPrefs.save();
    }
    private static void verifyTitleLayout() throws ReflectiveOperationException {
        Class<?> base=dev.mraz1visual.ui.menu.MenuBase.class;
        var listField=base.getDeclaredField("items"); listField.setAccessible(true);
        var heightField=base.getDeclaredField("vh"); heightField.setAccessible(true);
        var widthField=base.getDeclaredField("vw"); widthField.setAccessible(true);
        boolean fits=true;
        for(Object item:(java.util.List<?>)listField.get(title)) {
            int[] bounds=new int[4]; int i=0;
            for(String field:new String[]{"x","y","w","h"}) {var value=item.getClass().getDeclaredField(field);value.setAccessible(true);bounds[i++]=value.getInt(item);}
            fits &= bounds[0]>=0 && bounds[0]+bounds[2]<=widthField.getInt(title)
                    && bounds[1]>=0 && bounds[1]+bounds[3]<=heightField.getInt(title)-88;
        }
        check(fits,"all title menu rows fit above the brand at "+Minecraft.getInstance().getWindow().getWidth()+"x"+Minecraft.getInstance().getWindow().getHeight());
    }
    private static net.minecraft.network.chat.Component integrationText() {
        return net.minecraft.network.chat.Component.literal("Wim Москва 0123456789").withStyle(style -> style.withFont(
                new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual", "integration_native"))));
    }
    private static void initializeVisualFixtures(Minecraft mc,net.minecraft.world.item.ItemStack helmet) {
        try {
            Files.createDirectories(dev.mraz1visual.module.AuraParticles.CUSTOM_DIR);
            try(var input=IntegrationSmoke.class.getResourceAsStream("/assets/auravisuals/textures/particles/heart.png")) {
                Files.copy(java.util.Objects.requireNonNull(input),dev.mraz1visual.module.AuraParticles.CUSTOM_DIR.resolve("particle.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot prepare custom particle fixture",e);}
        var target=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("MrazVisualDummy".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"MrazVisualDummy"));
        target.setId(-800901);target.setPos(mc.player.getX()+1.5,mc.player.getY(),mc.player.getZ()+1.5);target.setNoGravity(true);
        target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet.copy());
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,1200));target.setInvisible(true);mc.level.addEntity(target);
        check(target.isInvisible()&&target.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)&&!target.isInvisibleTo(mc.player),"invisible player is locally visible while the invisibility effect stays intact");
        var armor=dev.mraz1visual.Mraz1Visual.MODULES.find("armor_durability_tag");
        ((dev.mraz1visual.core.AttackListener)armor).onAttack(target);
        var good=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
        good.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.SWIFTNESS));
        var bad=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
        bad.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.POISON));
        int goodColor=dev.mraz1visual.module.PotionHighlight.highlightColor(good),badColor=dev.mraz1visual.module.PotionHighlight.highlightColor(bad);
        check(goodColor!=0&&badColor!=0&&goodColor!=badColor&&dev.mraz1visual.module.PotionHighlight.highlightColor(helmet)==0,"potion highlights distinguish actual beneficial/harmful contents and ignore armor");
        mc.player.getInventory().setItem(0,good);mc.player.getInventory().setItem(1,bad);
        var frame=new net.minecraft.world.entity.decoration.ItemFrame(EntityType.ITEM_FRAME,mc.level);
        frame.setId(-800902);frame.setPos(mc.player.getX()-2,mc.player.getY()+1,mc.player.getZ()+1.5);
        frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));mc.level.addEntity(frame);
        check(dev.mraz1visual.module.NoFluids.hide(net.minecraft.world.level.material.FogType.WATER)
                &&dev.mraz1visual.module.NoFluids.hide(net.minecraft.world.level.material.FogType.LAVA)
                &&!dev.mraz1visual.module.NoFluids.hide(net.minecraft.world.level.material.FogType.NONE),"fluid fog controls affect only selected fluid types");
    }
    private static void verifySettingsPersistence() {
        var module=dev.mraz1visual.Mraz1Visual.MODULES.find("aura_wave");
        var range=(dev.mraz1visual.setting.SliderSetting)module.settings().stream().filter(s->s.id.equals("range")).findFirst().orElseThrow();
        boolean previousEnabled=module.isEnabled();double previousRange=range.get();
        module.setEnabled(true);range.set(7.5);dev.mraz1visual.Mraz1Visual.CONFIG.save();
        module.loadEnabled(false);range.setSilently(2);
        dev.mraz1visual.Mraz1Visual.CONFIG.load();dev.mraz1visual.Mraz1Visual.MODULES.afterConfigLoad();
        check(module.isEnabled()&&range.get()==7.5,"visual module state and settings survive save/load of real client config");
        module.setEnabled(previousEnabled);range.set(previousRange);dev.mraz1visual.Mraz1Visual.CONFIG.save();
    }
    private static void verifyCosmeticPersistence() {
        String previous=dev.mraz1visual.models.ModelPrefs.back;
        String id=ModelRegistry.all().stream().filter(e->e.collection.equals("aura")&&e.kind==dev.mraz1visual.models.ModelEntry.Kind.BACK).findFirst().orElseThrow().id;
        dev.mraz1visual.models.ModelPrefs.back=id;dev.mraz1visual.models.ModelPrefs.save();
        dev.mraz1visual.models.ModelPrefs.back="";dev.mraz1visual.models.ModelPrefs.load();
        check(dev.mraz1visual.models.ModelPrefs.back.equals(id),"imported cosmetic selection survives real models.json save/load");
        dev.mraz1visual.models.ModelPrefs.back=previous;dev.mraz1visual.models.ModelPrefs.save();
    }
    private static void finish(Minecraft mc) {
        step = -1;
        try { Files.writeString(Path.of(mc.gameDirectory.toString(),"integration-smoke-" + VERSION + ".txt"), report); }
        catch (Exception ignored) { }
        mc.stop();
    }
}
