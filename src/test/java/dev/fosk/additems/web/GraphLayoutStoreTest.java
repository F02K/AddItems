package dev.fosk.additems.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphLayoutStoreTest {

    @TempDir
    Path dir;

    @Test
    void startsEmptyAndPersistsAValidLayout() throws Exception {
        GraphLayoutStore store = new GraphLayoutStore(dir);
        assertEquals("LR", store.get().get("direction").getAsString());
        assertEquals(0, store.get().getAsJsonObject("positions").size());

        String body = "{\"direction\":\"TB\",\"positions\":{\"item:ruby\":{\"x\":10.6,\"y\":-20},\"source:CHEST:minecraft:chests/x\":{\"x\":0,\"y\":0}}}";
        assertNull(store.save(body));
        assertEquals("TB", store.get().get("direction").getAsString());
        assertEquals(11, store.get().getAsJsonObject("positions").getAsJsonObject("item:ruby").get("x").getAsInt(), "rounded");

        GraphLayoutStore reloaded = new GraphLayoutStore(dir);
        assertEquals(2, reloaded.get().getAsJsonObject("positions").size());
    }

    @Test
    void rejectsBadInput() throws Exception {
        GraphLayoutStore store = new GraphLayoutStore(dir);
        for (String bad : new String[]{
                "[]", "not json", "{\"direction\":\"UP\"}", "{\"positions\":[]}",
                "{\"positions\":{\"../x\":{\"x\":1,\"y\":1}}}", "{\"positions\":{\"item:a\":{\"x\":\"1\",\"y\":1}}}",
                "{\"positions\":{\"item:a\":{\"x\":1e12,\"y\":1}}}", "{\"positions\":{\"item:a\":5}}"}) {
            assertNotNull(store.save(bad), bad);
        }
        assertEquals(0, store.get().getAsJsonObject("positions").size(), "nothing stored after rejected saves");
    }

    @Test
    void unknownFieldsAreDroppedAndBrokenFilesIgnored() throws Exception {
        GraphLayoutStore store = new GraphLayoutStore(dir);
        assertNull(store.save("{\"direction\":\"LR\",\"evil\":\"<script>\",\"positions\":{\"item:a\":{\"x\":1,\"y\":2,\"z\":3}}}"));
        String stored = Files.readString(dir.resolve("graph-layout.json"));
        assertTrue(!stored.contains("evil") && !stored.contains("\"z\""));

        Files.writeString(dir.resolve("graph-layout.json"), "{ broken");
        assertEquals(0, new GraphLayoutStore(dir).get().getAsJsonObject("positions").size());
    }
}
