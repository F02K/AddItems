package dev.fosk.additems.block;

import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemFactory;
import dev.fosk.additems.item.ItemRegistry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Gameplay of custom blocks: placing, breaking (with custom hardness and drops), explosions, and keeping vanilla note
 * block behaviour (tuning, playing, instrument and power updates) from changing the states custom blocks live in.
 * Vanilla note blocks therefore do not play notes while AddItems is active.
 */
public final class CustomBlockListener implements Listener {

    /** Vanilla note block hardness; the break speed is scaled from it to the custom hardness. */
    private static final double NOTE_BLOCK_HARDNESS = 0.8;

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final CustomBlocks blocks;
    private final NamespacedKey hardnessKey;
    private final Random random = new Random();

    public CustomBlockListener(Plugin plugin, ItemRegistry items, ItemFactory factory, CustomBlocks blocks) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.blocks = blocks;
        this.hardnessKey = new NamespacedKey(plugin, "block_hardness");
    }

    // ------------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = factory.idOf(event.getItemInHand());
        if (id == null) return;
        ItemDefinition def = items.get(id);
        if (def == null || !def.block.placeable || def.block.state == null) return;

        Block block = event.getBlockPlaced();
        blocks.place(block, def);
        play(block, def.block.placeSound);
    }

    // ------------------------------------------------------------------ mining and breaking

    /** Scales the player's block break speed so the custom hardness applies to the block they start digging. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        ItemDefinition def = blocks.at(event.getBlock());
        if (def == null) {
            clearHardness(event.getPlayer());
        } else {
            applyHardness(event.getPlayer(), def.block.hardness);
        }
    }

    @EventHandler
    public void onDamageAbort(BlockDamageAbortEvent event) {
        clearHardness(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearHardness(event.getPlayer());
    }

    /**
     * Runs after loot rules (HIGH): a rule that replaces drops has already switched drops off, in which case the block
     * does not drop itself either.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ItemDefinition def = blocks.at(block);
        Player player = event.getPlayer();
        clearHardness(player);
        if (def == null) return;

        boolean replacedByLoot = !event.isDropItems();
        event.setDropItems(false); // never drop the plain note block item
        GameMode mode = player.getGameMode();
        if (!replacedByLoot && mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
            dropSelf(block, def, player.getInventory().getItemInMainHand());
        }
        play(block, def.block.breakSound);
    }

    private void dropSelf(Block block, ItemDefinition def, ItemStack tool) {
        if (!def.block.dropSelf) return;
        if (def.block.requiresTool && !blocks.toolMatches(def.block.preferredTool, tool)) return;
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(at, factory.create(def, 1));
    }

    private void applyHardness(Player player, double hardness) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed == null) return;
        speed.removeModifier(hardnessKey);
        double multiplier = NOTE_BLOCK_HARDNESS / Math.max(0.05, hardness);
        speed.addModifier(new AttributeModifier(hardnessKey, multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void clearHardness(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed != null) speed.removeModifier(hardnessKey);
    }

    // ------------------------------------------------------------------ explosions

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList(), event.getYield());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList(), event.getYield());
    }

    /** Custom blocks in an explosion drop their own item (with the explosion's yield chance), not a note block. */
    private void handleExplosion(List<Block> destroyed, float yield) {
        for (Iterator<Block> it = destroyed.iterator(); it.hasNext(); ) {
            Block block = it.next();
            ItemDefinition def = blocks.at(block);
            if (def == null) continue;
            it.remove();
            if (def.block.dropSelf && random.nextFloat() <= yield) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), factory.create(def, 1));
            }
            block.setType(Material.AIR, false);
        }
    }

    // ------------------------------------------------------------------ protection from note block behaviour

    /** Right-clicking a note block tunes it, which would turn a custom block into another one. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType() == Material.NOTE_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onNotePlay(NotePlayEvent event) {
        event.setCancelled(true);
    }

    /**
     * Stops neighbour updates from changing a note block's powered state. The instrument is changed by a shape update
     * that no Bukkit event covers; it needs Paper's {@code block-updates.disable-noteblock-updates} (see
     * {@link CustomBlocks#checkServerSetup}).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NOTE_BLOCK) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ helpers

    private void play(Block block, String sound) {
        if (sound == null || sound.isBlank()) return;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 1f, 1f);
    }
}
