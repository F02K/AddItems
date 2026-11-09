package dev.fosk.additems.recipe;

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

/** Loads, stores and persists recipe definitions (one JSON file per recipe). */
public final class RecipeRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;
    private final String customNamespace;
    private final Logger log;
    private final Map<String, RecipeDefinition> recipes = new ConcurrentHashMap<>();

    public RecipeRegistry(Path dataFolder, String customNamespace, Logger log) {
        this.dir = dataFolder.resolve("recipes");
        this.customNamespace = customNamespace;
        this.log = log;
    }

    public String customNamespace() {
        return customNamespace;
    }

    public void load() throws IOException {
        Files.createDirectories(dir);
        recipes.clear();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    RecipeDefinition def = GSON.fromJson(reader, RecipeDefinition.class);
                    String error = def == null ? "empty file" : def.validate(customNamespace);
                    if (error != null) {
                        log.warning("Skipping recipe " + file.getFileName() + ": " + error);
                        continue;
                    }
                    recipes.put(def.id, def);
                } catch (Exception e) {
                    log.warning("Skipping recipe " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }
    }

    public List<RecipeDefinition> all() {
        List<RecipeDefinition> list = new ArrayList<>(recipes.values());
        list.sort(Comparator.comparing(r -> r.id));
        return list;
    }

    public RecipeDefinition get(String id) {
        return id == null ? null : recipes.get(id);
    }

    /** Validates and stores the recipe. Returns an error message or null on success. */
    public synchronized String save(RecipeDefinition def) throws IOException {
        String error = def.validate(customNamespace);
        if (error != null) return error;
        Path file = dir.resolve(def.id + ".json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(def, writer);
        }
        recipes.put(def.id, def);
        return null;
    }

    public synchronized boolean delete(String id) throws IOException {
        if (id == null || !RecipeDefinition.ID_PATTERN.matcher(id).matches()) return false;
        boolean existed = recipes.remove(id) != null;
        Files.deleteIfExists(dir.resolve(id + ".json"));
        return existed;
    }
}
