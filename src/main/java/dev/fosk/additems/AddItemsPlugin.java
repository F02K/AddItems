package dev.fosk.additems;

import dev.fosk.additems.block.CustomBlockListener;
import dev.fosk.additems.block.CustomBlocks;
import dev.fosk.additems.block.SoundCatalog;
import dev.fosk.additems.item.ItemFactory;
import dev.fosk.additems.item.ItemRegistry;
import dev.fosk.additems.pack.PackBuilder;
import dev.fosk.additems.pack.PackFormat;
import dev.fosk.additems.pack.PackFormats;
import dev.fosk.additems.vanilla.VanillaAssets;
import dev.fosk.additems.loot.LootManager;
import dev.fosk.additems.loot.LootRegistry;
import dev.fosk.additems.recipe.RecipeManager;
import dev.fosk.additems.recipe.RecipeRegistry;
import dev.fosk.additems.web.AuthManager;
import dev.fosk.additems.web.DashboardServer;
import dev.fosk.additems.web.GraphLayoutStore;
import dev.fosk.additems.web.PackHost;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;

public final class AddItemsPlugin extends JavaPlugin implements Listener, DashboardServer.Actions {

    /** Stable id so a re-sent pack replaces the previous AddItems pack on the client. */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("additems-pack".getBytes());

    private ItemRegistry registry;
    private ItemFactory factory;
    private VanillaAssets vanilla;
    private RecipeRegistry recipeRegistry;
    private RecipeManager recipeManager;
    private CustomBlocks customBlocks;
    private SoundCatalog sounds;
    private LootRegistry lootRegistry;
    private LootManager lootManager;
    private AuthManager auth;
    private PackFormat packFormat;
    private PackHost packHost;
    private DashboardServer dashboard;
    private volatile PackBuilder.Result lastPack;
    private String minecraftVersion;
    private String namespace;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        minecraftVersion = getPluginMeta().getAPIVersion();
        namespace = getConfig().getString("pack.namespace", "additems");
        packFormat = PackFormats.forVersion(minecraftVersion);
        if (!PackFormats.isSupported(minecraftVersion)) {
            getLogger().warning("No pack format for Minecraft " + minecraftVersion + ", using "
                    + packFormat.minecraftVersion() + ". Generated packs may not load.");
        }

        registry = new ItemRegistry(getDataFolder().toPath(), getLogger());
        factory = new ItemFactory(this, namespace);
        customBlocks = new CustomBlocks(registry, factory);
        sounds = new SoundCatalog();
        registry.setSoundValidator(sounds::isValid);
        registry.setStatePool(customBlocks.statePool()); // before load(): hand-written blocks get their state then
        customBlocks.checkServerSetup(getServer().getPluginsFolder().getAbsoluteFile().getParentFile());
        if (customBlocks.setupProblem() != null) getLogger().warning(customBlocks.setupProblem());
        try {
            registry.load();
        } catch (Exception e) {
            getLogger().severe("Could not load items: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(
                new CustomBlockListener(this, registry, factory, customBlocks), this);
        auth = new AuthManager();

        recipeRegistry = new RecipeRegistry(getDataFolder().toPath(), namespace, getLogger());
        try {
            recipeRegistry.load();
        } catch (Exception e) {
            getLogger().severe("Could not load recipes: " + e.getMessage());
        }
        recipeManager = new RecipeManager(this, registry, factory, recipeRegistry);
        getServer().getPluginManager().registerEvents(recipeManager, this);
        recipeManager.apply();

        lootRegistry = new LootRegistry(getDataFolder().toPath(), namespace, getLogger());
        try {
            lootRegistry.load();
        } catch (Exception e) {
            getLogger().severe("Could not load loot rules: " + e.getMessage());
        }
        lootManager = new LootManager(this, registry, factory, lootRegistry, customBlocks);
        getServer().getPluginManager().registerEvents(lootManager, this);
        lootManager.apply();

        vanilla =new VanillaAssets(getDataFolder().toPath(), minecraftVersion,
                getConfig().getBoolean("vanilla-assets.enabled", true), getLogger());
        Bukkit.getScheduler().runTaskAsynchronously(this, vanilla::ensureReady);

        startServers();
        restoreLastPack();

        AddItemsCommand command = new AddItemsCommand(this);
        getCommand("additems").setExecutor(command);
        getCommand("additems").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Loaded " + registry.all().size() + " items and " + recipeRegistry.all().size()
                + " recipes (" + recipeManager.registeredCount() + " active) and " + lootRegistry.all().size()
                + " loot rules (" + lootManager.activeCount() + " active) for Minecraft " + minecraftVersion + ".");
    }

    @Override
    public void onDisable() {
        if (dashboard != null) dashboard.stop();
        if (packHost != null) packHost.stop();
    }

    private void startServers() {
        try {
            String publicUrl = getConfig().getString("dashboard.public-url", "");
            boolean secure = publicUrl.startsWith("https://");
            packHost = new PackHost(getConfig().getString("pack.bind", "0.0.0.0"),
                    getConfig().getInt("pack.port", 8124));
            boolean samePort = packOnDashboardPort();
            dashboard = new DashboardServer(dashboardBind(),
                    getConfig().getInt("dashboard.port", 8123), secure, registry, recipeRegistry, lootRegistry, vanilla,
                    new GraphLayoutStore(getDataFolder().toPath()), auth, this, samePort ? packHost : null, getLogger());
            dashboard.start();
            logDashboardAccess();
            if (samePort) {
                getLogger().info("The resource pack is served on the dashboard port (/pack/<sha1>.zip, no login needed for it).");
            } else {
                packHost.start();
            }
        } catch (Exception e) {
            getLogger().severe("Could not start web servers: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ accessors

    public ItemRegistry registry() { return registry; }
    public ItemFactory factory() { return factory; }
    public AuthManager auth() { return auth; }

    /** The address the dashboard listens on: all interfaces when public access is on, else the configured local address. */
    String dashboardBind() {
        if (getConfig().getBoolean("dashboard.public-access", false)) return "0.0.0.0";
        return getConfig().getString("dashboard.bind", "127.0.0.1");
    }

    /** pack.host: dashboard = players download the pack from the dashboard's port, separate = its own port. */
    private boolean packOnDashboardPort() {
        return "dashboard".equalsIgnoreCase(getConfig().getString("pack.host", "separate"));
    }

    /** Base URL players download the pack from: pack.public-url, or the dashboard address when it serves the pack. */
    private String packBaseUrl() {
        String configured = getConfig().getString("pack.public-url", "");
        if (!configured.isBlank()) return configured.replaceAll("/+$", "");
        if (packOnDashboardPort() && !dashboardIsLocalOnly()) return dashboardUrl();
        return "";
    }

    private boolean dashboardIsLocalOnly() {
        String bind = dashboardBind();
        return bind.equals("127.0.0.1") || bind.equals("localhost") || bind.equals("::1");
    }

    /** The address team members open the dashboard at (used in login links). */
    public String dashboardUrl() {
        String url = getConfig().getString("dashboard.public-url", "");
        if (!url.isBlank()) return url.replaceAll("/+$", "");
        int port = getConfig().getInt("dashboard.port", 8123);
        if (!dashboardIsLocalOnly()) {
            String ip = getServer().getIp();
            return "http://" + (ip == null || ip.isBlank() ? "localhost" : ip) + ":" + port;
        }
        return "http://" + dashboardBind() + ":" + port;
    }

    /** Why the login link will not work for other people yet, or null. Shown by /additems login and in the log. */
    public String dashboardAccessHint() {
        if (dashboardIsLocalOnly()) return null;
        String url = getConfig().getString("dashboard.public-url", "");
        String ip = getServer().getIp();
        if (url.isBlank() && (ip == null || ip.isBlank())) {
            return "dashboard.public-url is empty: set it to the address people use (e.g. http://play.example.com:"
                    + getConfig().getInt("dashboard.port", 8123) + "), otherwise the link points to localhost";
        }
        return null;
    }

    private void logDashboardAccess() {
        int port = getConfig().getInt("dashboard.port", 8123);
        if (dashboardIsLocalOnly()) {
            getLogger().info("Dashboard on " + dashboardUrl() + " (this machine only). To let your team in, set "
                    + "dashboard.public-access: true and open TCP port " + port + ".");
            return;
        }
        getLogger().info("Dashboard listening on all interfaces: " + dashboardUrl() + " - TCP port " + port
                + " must be open in the firewall / hosting panel for others to reach it.");
        String hint = dashboardAccessHint();
        if (hint != null) getLogger().warning(hint);
        if (!getConfig().getString("dashboard.public-url", "").startsWith("https://")) {
            getLogger().warning("The dashboard is reachable over plain HTTP. Use an HTTPS reverse proxy for anything but a "
                    + "private network (login links and session cookies are otherwise readable on the way).");
        }
    }

    // ------------------------------------------------------------------ pack

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public PackBuilder.Result lastPack() {
        return lastPack;
    }

    @Override
    public PackBuilder.Result buildPack() throws Exception {
        Path target = getDataFolder().toPath().resolve("pack").resolve("pack.zip");
        PackBuilder.Result result = new PackBuilder(registry, packFormat, namespace, customBlocks.allVariants()).build(target);
        lastPack = result;
        packHost.publish(result.sha1(), result.file());
        Bukkit.getScheduler().runTask(this, () -> Bukkit.getOnlinePlayers().forEach(this::sendPack));
        return result;
    }

    /** Why the pack cannot be sent right now, or null if it can. */
    public String packProblem() {
        if (lastPack == null) return "no pack built yet - run /additems pack build or use the dashboard";
        if (packOnDashboardPort() && dashboardIsLocalOnly()) {
            return "pack.host is 'dashboard' but the dashboard is local only: set dashboard.public-access: true "
                    + "(and open its port) so players can download the pack - then restart";
        }
        if (packBaseUrl().isBlank()) {
            return "pack.public-url is empty in config.yml (e.g. http://localhost:8124) - then /additems reload";
        }
        return null;
    }

    /** After a restart, serve the previously built pack again instead of forgetting it. */
    private void restoreLastPack() {
        Path file = getDataFolder().toPath().resolve("pack").resolve("pack.zip");
        if (!java.nio.file.Files.isRegularFile(file)) return;
        try {
            PackBuilder.Result result = PackBuilder.describe(file);
            lastPack = result;
            packHost.publish(result.sha1(), result.file());
            getLogger().info("Restored existing pack " + result.sha1().substring(0, 8));
        } catch (Exception e) {
            getLogger().warning("Could not restore existing pack: " + e.getMessage());
        }
    }

    public void sendPack(Player player) {
        String problem = packProblem();
        if (problem != null) {
            getLogger().warning("Not sending pack to " + player.getName() + ": " + problem);
            return;
        }
        PackBuilder.Result pack = lastPack;
        String url = packBaseUrl() + "/pack/" + pack.sha1() + ".zip";
        getLogger().info("Sending pack " + pack.sha1().substring(0, 8) + " to " + player.getName() + " (" + url + ")");
        player.setResourcePack(PACK_ID, url, HexFormat.of().parseHex(pack.sha1()),
                Component.text(getConfig().getString("pack.prompt", "")),
                getConfig().getBoolean("pack.required", false));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("pack.send-on-join", true)) sendPack(event.getPlayer());
    }

    public void reloadAll() throws Exception {
        reloadConfig();
        registry.load();
        recipeRegistry.load();
        recipeManager.apply();
        lootRegistry.load();
        lootManager.apply();
    }

    @Override
    public java.util.List<String> soundNames() {
        return sounds.names();
    }

    @Override
    public String blockSetupProblem() {
        return customBlocks.setupProblem();
    }

    @Override
    public String lootProblem(String ruleId) {
        return lootManager.problem(ruleId);
    }

    // ------------------------------------------------------------------ recipes

    public CustomBlocks customBlocks() { return customBlocks; }
    public RecipeManager recipeManager() { return recipeManager; }
    public RecipeRegistry recipeRegistry() { return recipeRegistry; }

    @Override
    public String recipeProblem(String recipeId) {
        return recipeManager.problem(recipeId);
    }

    /** Called from the web thread: re-registers recipes on the server thread and waits for it. */
    @Override
    public void contentChanged() {
        try {
            Bukkit.getScheduler().callSyncMethod(this, () -> {
                recipeManager.apply();
                lootManager.apply();
                return null;
            }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            getLogger().warning("Could not apply recipes: " + e.getMessage());
        }
    }
}
