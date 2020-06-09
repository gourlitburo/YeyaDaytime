package yy.gourlitburo.yeyadaytime;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin {
    /* fields */

    Logger logger = getLogger();

    private List<World> worlds = new ArrayList<>();
    private long lastSystemTime;

    private BukkitRunnable task;

    /* methods */

    private long toMCInterval(long milliseconds) {
        double minutes = (double)milliseconds / 1000 / 60;
        return Math.round(minutes * 24000 / 1440);
    }

    private void startTask() {
        try {
            try {
                if (task != null) task.cancel(); // otherwise we have two tasks running
            } catch (IllegalStateException e) { /* pass */ }

            lastSystemTime = System.currentTimeMillis();

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    long currentSystemTime = System.currentTimeMillis();
                    long ticksDelta = toMCInterval(currentSystemTime - lastSystemTime);
                    lastSystemTime = currentSystemTime;
                    for (World world : worlds) {
                        try {
                            long worldTime = world.getTime() + ticksDelta;
                            world.setTime(worldTime);
                            // logger.info(String.format("Time in '%s' -> %d", world.getName(), worldTime));
                        } catch (NullPointerException e) {
                            logger.warning("Failed to set world time: NullPointerException");
                        } catch (Exception e) {
                            logger.warning("Failed to set world time.");
                        }
                    }
                }
            };
            task.runTaskTimer(this, 0L, 50L * 20);
            logger.info("Task started successfully.");
        } catch (IllegalStateException e) {
            logger.info("Task already started.");
        }
    }

    private void stopTask() {
        if (!task.isCancelled()) {
            task.cancel();
            logger.info("Task stopped successfully.");
        } else {
            logger.info("Task already stopped.");
        }
    }

    private void configChanged() {
        logger.info("Processing config...");

        FileConfiguration config = getConfig();

        // update worlds list
        worlds.clear();
        for (String worldName : config.getStringList("worlds")) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning(String.format("Could not find world '%s'.", worldName));
                continue;
            }
            worlds.add(world);
        }

        // enable/disable
        if (config.getBoolean("enable") == true) {
            lastSystemTime = System.currentTimeMillis();
            startTask();
        } else {
            stopTask();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = args[0];
        if (cmd.equalsIgnoreCase("reload")) {
            reloadConfig();
            configChanged();
            sender.sendMessage("YeyaDaytime reloaded.");
            return true;
        } else if (cmd.equalsIgnoreCase("enable")) {
            getConfig().set("enable", true);
            saveConfig();
            configChanged();
            sender.sendMessage("YeyaDaytime enabled. Please adjust doDaylightCycle as needed.");
            return true;
        } else if (cmd.equalsIgnoreCase("disable")) {
            getConfig().set("enable", false);
            saveConfig();
            configChanged();
            sender.sendMessage("YeyaDaytime disabled. Please adjust doDaylightCycle as needed.");
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        getCommand("yeyadaytime").setExecutor(this);
        saveDefaultConfig();

        configChanged();

        logger.info("YeyaDaytime ready.");
    }
}
