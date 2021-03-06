package de.jeff_media.Drop2Inventory;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Listener implements org.bukkit.event.Listener {

    Main plugin;
    Random random = new Random();
    boolean onlyDamaged;
    PlantUtils plantUtils = new PlantUtils();

    Listener(Main plugin) {
        this.plugin = plugin;
        boolean onlyDamaged = plugin.mcVersion >= 16 ? true : false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        plugin.registerPlayer(event.getPlayer());


    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.unregisterPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.debug("EntityDeathEvent");
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() == null) {
            plugin.debug("Return: entity.getKiller = null");
            return;
        }

        if(plugin.isWorldDisabled(event.getEntity().getWorld().getName())) {
            return;
        }

        if(!(entity.getKiller() instanceof Player)) {
            plugin.debug("Return: entity.getKiller ! instanceof player");
            return;
        }

        Player p = (Player) entity.getKiller();

        if (!entity.getKiller().hasPermission("drop2inventory.use")) {
            plugin.debug("Return: entity.getKiller ! permission drop2inventory.use");
            return;
        }

        // Fix for /reload
        plugin.registerPlayer(entity.getKiller());

        if (!plugin.enabled(entity.getKiller())) {
            plugin.debug("entity.getKiller ! drop2Inv enabled");
            return;
        }

        // Mobs drop stuff in Creative mode
        //if (entity.getKiller().getGameMode() == GameMode.CREATIVE) {
        //    return;
        //}

        if (!plugin.utils.isMobEnabled(entity)) {
            plugin.debug("not enabled for entity type "+entity.getType().name());
            return;
        }


        if (plugin.getConfig().getBoolean("collect-mob-exp")) {
            int exp = event.getDroppedExp();

            if(MendingUtils.hasMending(plugin.utils.getItemInMainHand(p),false)) {
                exp = plugin.mendingUtils.tryMending(p.getInventory(), exp, onlyDamaged);
            }

            event.setDroppedExp(0);
            entity.getKiller().giveExp(exp);
        }
        if (!plugin.getConfig().getBoolean("collect-mob-drops")) {
            return;
        }

        //entity.getKiller().sendMessage("You have killed entity "+entity.getName());

        List<ItemStack> drops = event.getDrops();
        plugin.debug("Dropping contents for entity kill to player inv");
        plugin.utils.addOrDrop(drops.toArray(new ItemStack[0]),entity.getKiller());
        event.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.MONITOR) // Monitor because some plugins like BannerBoard are too stupid to listen on LOWEST when they only want to cancel events regarding their own stuff...
    public void onItemFrameRemoveItem(EntityDamageByEntityEvent event) {

        if(!(event.getDamager() instanceof Player)) return;
        Player p = (Player) event.getDamager();
        if(event.isCancelled()) {
            plugin.debug("EntityDamageByEntityEvent is cancelled");
            return;
        }
        plugin.debug("EntityDamageByEntityEvent is NOT cancelled");
        if(!isDrop2InvEnabled(p,plugin.getPlayerSetting(p))) return;

        if(plugin.isWorldDisabled(p.getWorld().getName())) {
            return;
        }

        if(event.getEntity() instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) event.getEntity();
            ItemStack content = frame.getItem();
            if(content != null && content.getType()!=Material.AIR) {
                plugin.debug("The frame contained "+content.toString());
                plugin.utils.addOrDrop(content,p);
            } else {
                return;
            }
            frame.setItem(null);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) // Monitor because some plugins like BannerBoard are too stupid to listen on LOWEST when they only want to cancel events regarding their own stuff...
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if(!(event.getRemover() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getRemover();
        if(plugin.isWorldDisabled(p.getName())) {
            return;
        }
        if(event.isCancelled()) return;
        if(!isDrop2InvEnabled(p,plugin.getPlayerSetting(p))) return;
        plugin.debug("Player removed a Hanging");
        if(event.getEntity() instanceof ItemFrame) {
            plugin.debug("It was an Item frame.");
            ItemFrame frame = (ItemFrame) event.getEntity();
            ItemStack content = frame.getItem();
            if(content != null) {
                plugin.debug("The frame contained "+content.toString());
                plugin.utils.addOrDrop(content,p);
            }
            plugin.utils.addOrDrop(new ItemStack(Material.ITEM_FRAME),p);
            event.getEntity().remove();
            event.setCancelled(true);
        }

        if(event.getEntity() instanceof Painting) {
            plugin.utils.addOrDrop(new ItemStack(Material.PAINTING),p);
            event.getEntity().remove();
        }

    }




    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {

        //System.out.println("BlockBreakEvent "+event.getBlock().getType().name());


        // TODO: Drop shulker box to inv but keep contents
		/*if (event.getBlock().getType().name().toLowerCase().endsWith("shulker_box")) {
			return;
		}*/

        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();



        // disabled block?
        if (!plugin.utils.isBlockEnabled(event.getBlock().getType())) {
            return;
        }

        if(plugin.isWorldDisabled(player.getWorld().getName())) {
            return;
        }






        PlayerSetting setting = plugin.perPlayerSettings.get(player.getUniqueId().toString());

        if (!isDrop2InvEnabled(player, setting)) return;

        if (plugin.enabled(player) && plugin.getConfig().getBoolean("collect-block-exp")) {
            int experience = event.getExpToDrop();
            if(MendingUtils.hasMending(plugin.utils.getItemInMainHand(event.getPlayer()),false)) {
                experience = plugin.mendingUtils.tryMending(event.getPlayer().getInventory(), experience,onlyDamaged);
            }
            event.getPlayer().giveExp(experience);
            event.setExpToDrop(0);
        }

        if(plantUtils.isPlant(event.getBlock())) {
            event.setDropItems(false);
            ArrayList<Block> plant = PlantUtils.getPlant(event.getBlock());
            int extraAmount = plant.size();
            ItemStack plantItems = new ItemStack(PlantUtils.getPlantDrop(event.getBlock().getType()), extraAmount);
            plugin.utils.addOrDrop(plantItems,event.getPlayer());
            PlantUtils.destroyPlant(plant);
        } else if(PlantUtils.isChorusTree(event.getBlock())) {
            // Note:
            // Chorus flower only drop themselves when broken directly,
            // but not when they drop because the chorus plant is broken
            ArrayList<Block> chorusTree = new ArrayList<Block>();
            event.setDropItems(false);
             PlantUtils.getChorusTree(event.getBlock(),chorusTree);
            int extraAmountChorusPlant = PlantUtils.getAmountInList(chorusTree,Material.CHORUS_PLANT);
            int extraAmountChorusFruit = 0;

            for(int i = 0; i < extraAmountChorusPlant; i++) {
                if(random.nextInt(100)>=50) {
                    extraAmountChorusFruit++;
                }
            }

            ItemStack flowerDrops = new ItemStack(Material.CHORUS_FRUIT, extraAmountChorusFruit);
            plugin.utils.addOrDrop(flowerDrops,event.getPlayer());
            PlantUtils.destroyPlant(chorusTree);
        } else if(event.getBlock().getState() instanceof Furnace) {

            FurnaceInventory finv = ((Furnace) event.getBlock().getState()).getInventory();

            if(finv.getFuel()!=null) {
                plugin.utils.addOrDrop(finv.getFuel(),event.getPlayer());
                finv.setFuel(null);
            }
            if(finv.getSmelting()!=null) {
                plugin.utils.addOrDrop(finv.getSmelting(),event.getPlayer());
                finv.setSmelting(null);
            }
            if(finv.getResult()!=null) {
                plugin.utils.addOrDrop(finv.getResult(),event.getPlayer());
                finv.setResult(null);
            }


        }



        //plugin.dropHandler.drop2inventory(event);
    }

    private boolean isDrop2InvEnabled(Player player, PlayerSetting setting) {

        if(plugin.getConfig().getBoolean("always-enabled")) return true;

        if (!player.hasPermission("drop2inventory.use")) {
            return false;
        }

        // Fix for /reload
        plugin.registerPlayer(player);

        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }



        if (!plugin.getConfig().getBoolean("collect-block-drops")) {
            return false;
        }
        if (!plugin.enabled(player)) {
            if (!setting.hasSeenMessage) {
                setting.hasSeenMessage = true;
                if (plugin.getConfig().getBoolean("show-message-when-breaking-block")) {
                    player.sendMessage(plugin.messages.MSG_COMMANDMESSAGE);
                }
            }
            return false;
        } else {
            if (!setting.hasSeenMessage) {
                setting.hasSeenMessage = true;
                if (plugin.getConfig().getBoolean("show-message-when-breaking-block-and-collection-is-enabled")) {
                    player.sendMessage(plugin.messages.MSG_COMMANDMESSAGE2);
                }
            }
        }
        return true;
    }

}
