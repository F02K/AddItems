package dev.fosk.additems.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Loads, stores and persists loot rules (one JSON file per rule in plugins/AddItems/loot/). */
public final class LootRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;
    private final String customNamespace;
    private final Logger log;
    private final Map<String, LootRule> rules = new ConcurrentHashMap<>();

    public LootRegistry(Path dataFolder, String customNamespace, Logger log) {
        this.dir = dataFolder.resolve("loot");
        this.customNamespace = customNamespace;
        this.log = log;
    }

    public String customNamespace() {
        return customNamespace;
    }

    public void load() throws IOException {
        Files.createDirectories(dir);
        rules.clear();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    LootRule rule = GSON.fromJson(reader, LootRule.class);
                    String error = rule == null ? "empty file" : rule.validate(customNamespace);
                    if (error != null) {
                        log.warning("Skipping loot rule " + file.getFileName() + ": " + error);
                        continue;
                    }
                    rules.put(rule.id, rule);
                } catch (Exception e) {
                    log.warning("Skipping loot rule " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }
    }

    public List<LootRule> all() {
        List<LootRule> list = new ArrayList<>(rules.values());
        list.sort(Comparator.comparing(r -> r.id));
        return list;
    }

    public LootRule get(String id) {
        return id == null ? null : rules.get(id);
    }

    /** Validates and stores the rule. Returns an error message or null on success. */
    public synchronized String save(LootRule rule) throws IOException {
        String error = rule.validate(customNamespace);
        if (error != null) return error;
        Path file = dir.resolve(rule.id + ".json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(rule, writer);
        }
        rules.put(rule.id, rule);
        return null;
    }

    public synchronized boolean delete(String id) throws IOException {
        if (id == null || !LootRule.ID_PATTERN.matcher(id).matches()) return false;
        boolean existed = rules.remove(id) != null;
        Files.deleteIfExists(dir.resolve(id + ".json"));
        return existed;
    }
}
