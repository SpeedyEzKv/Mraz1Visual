package dev.umbra.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.umbra.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Аккаунты Microsoft: список, активный, вход, тихий повторный вход при запуске.
 * Файл: config/umbra/accounts.json. Там же client_id приложения Azure.
 * Хранятся токены (не пароль). Файл не отправляй никому.
 */
public final class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("umbra").resolve("accounts.json");

    public static final class Account {
        public String name;
        public String uuid;
        public String refresh;
        public transient String access;
        public transient long expiresAt;
        public String xuid = "";
    }

    private static final class Data {
        String client_id = "";
        String active = "";
        List<Account> accounts = new ArrayList<>();
    }

    private static Data data = new Data();

    /** Что происходит сейчас — для экрана. */
    public static volatile String status = "";
    public static volatile MicrosoftAuth.DeviceCode pendingCode;
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static volatile boolean cancel;

    private AccountStore() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), new TypeToken<Data>() {}.getType());
                if (d != null) {
                    if (d.accounts == null) d.accounts = new ArrayList<>();
                    if (d.client_id == null) d.client_id = "";
                    if (d.active == null) d.active = "";
                    data = d;
                }
            } else {
                save();
            }
        } catch (Throwable t) {
            ModuleManager.LOG.warn("accounts.json unreadable ({}), starting empty", t.toString());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("accounts.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            ModuleManager.LOG.warn("Cannot save accounts", e);
        }
    }

    public static List<Account> accounts() { return data.accounts; }

    public static String activeUuid() { return data.active; }

    public static boolean hasClientId() { return !data.client_id.isBlank(); }

    public static boolean isBusy() { return busy.get(); }

    /** Добавить аккаунт: получить код, ждать ввода на сайте. */
    public static void startLogin() {
        if (!hasClientId()) {
            status = "Нет Client ID. Впиши его в config/umbra/accounts.json (см. README).";
            return;
        }
        if (!busy.compareAndSet(false, true)) return;
        cancel = false;
        status = "Получаю код…";
        Thread t = new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestCode(data.client_id);
                pendingCode = code;
                status = "Введи код на сайте Microsoft";
                MicrosoftAuth.Result r = MicrosoftAuth.poll(data.client_id, code, () -> cancel);
                pendingCode = null;
                Minecraft.getInstance().execute(() -> {
                    Account a = upsert(r);
                    activate(a);
                    status = "Вошёл как " + a.name;
                });
            } catch (MicrosoftAuth.AuthException e) {
                status = e.getMessage();
            } catch (Exception e) {
                status = "Ошибка сети: " + e.getClass().getSimpleName();
                ModuleManager.LOG.warn("Microsoft login failed", e);
            } finally {
                pendingCode = null;
                busy.set(false);
            }
        }, "Umbra login");
        t.setDaemon(true);
        t.start();
    }

    public static void cancelLogin() {
        cancel = true;
        pendingCode = null;
        status = "";
    }

    /** Переключиться на сохранённый аккаунт (обновляет токен в фоне). */
    public static void switchTo(Account a) {
        if (!hasClientId() || !busy.compareAndSet(false, true)) return;
        status = "Вхожу как " + a.name + "…";
        Thread t = new Thread(() -> {
            try {
                MicrosoftAuth.Result r = MicrosoftAuth.refresh(data.client_id, a.refresh);
                Minecraft.getInstance().execute(() -> {
                    Account fresh = upsert(r);
                    activate(fresh);
                    status = "Вошёл как " + fresh.name;
                });
            } catch (MicrosoftAuth.AuthException e) {
                status = a.name + ": " + e.getMessage();
            } catch (Exception e) {
                status = "Ошибка сети: " + e.getClass().getSimpleName();
            } finally {
                busy.set(false);
            }
        }, "Umbra login");
        t.setDaemon(true);
        t.start();
    }

    /** При запуске игры — тихо войти в последний активный аккаунт. */
    public static void autoLogin() {
        if (data.active.isBlank()) return;
        for (Account a : data.accounts) {
            if (a.uuid.equals(data.active)) {
                switchTo(a);
                return;
            }
        }
    }

    public static void remove(Account a) {
        data.accounts.remove(a);
        if (a.uuid.equals(data.active)) data.active = "";
        save();
    }

    private static Account upsert(MicrosoftAuth.Result r) {
        Account a = null;
        for (Account x : data.accounts) if (x.uuid.equals(r.uuid())) a = x;
        if (a == null) {
            a = new Account();
            data.accounts.add(a);
        }
        a.name = r.name();
        a.uuid = r.uuid();
        a.refresh = r.refreshToken();
        a.access = r.accessToken();
        a.expiresAt = r.expiresAt();
        a.xuid = r.xuid();
        return a;
    }

    private static void activate(Account a) {
        data.active = a.uuid;
        save();
        SessionSwap.apply(a.name, a.uuid, a.access, a.xuid);
    }

    /** Активен ли сейчас в игре этот аккаунт. */
    public static boolean isCurrent(Account a) {
        return a.uuid.equals(data.active) && Minecraft.getInstance().getUser().getName().equals(a.name);
    }
}
