package yy.gourlitburo.yeyadaytime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin {
    /* fields */

    Logger logger = getLogger();

    private List<World> worlds = new ArrayList<>();
    private Map<UUID, Long> baseSystemTimes = new HashMap<>(4);
    private Map<UUID, Long> baseWorldTimes = new HashMap<>(4);

    private final Pattern patTimeNamed = Pattern.compile("day|night|dawn");
    private final Pattern patTimeFull = Pattern.compile("(\\d{2}):(\\d{2})");
    private final Pattern patTimeShort = Pattern.compile("(\\d+)(am|AM|pm|PM)");
    private final Pattern patTimeTicks = Pattern.compile("(\\d+)ticks");

    private BukkitRunnable task;

    /* methods */

    private long toMCInterval(long milliseconds) {
        double minutes = (double)milliseconds / 1000 / 60;
        return Math.round(minutes * 24000 / 1440);
    }

    private long parseTimeString(String time) {
        Matcher m;

        m = patTimeNamed.matcher(time);
        if (m.matches()) {
            String name = m.group(0);
            if (name.equals("day")) return 1000;
            if (name.equals("night")) return 13000;
            if (name.equals("dawn")) return 23000; // BE's /time set sunrise
        }

        m = patTimeFull.matcher(time);
        if (m.matches()) {
            int hr = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));

            long ticks = (hr - 6) * 1000;
            ticks += min * (16 + 2 / 3); // 16.666...
            return ticks;
        }

        m = patTimeShort.matcher(time);
        if (m.matches()) {
            int hr = Integer.parseInt(m.group(1));
            int period = m.group(2).equalsIgnoreCase("am") ? 0 : 1;

            long ticks = period == 1 ? 6000 : 0;
            ticks += (hr - 6) * 1000;
            return ticks;
        }

        m = patTimeTicks.matcher(time);
        if (m.matches()) {
            long ticks = Integer.parseInt(m.group(1));
            return ticks;
        }

        throw new IllegalArgumentException("Invalid time string.");
    }

    private void startTask() {
        try {
            try {
                if (task != null) task.cancel(); // otherwise we have two tasks running
            } catch (IllegalStateException e) { /* pass */ }

            // set base times
            baseSystemTimes.clear();
            baseWorldTimes.clear();
            long sysTime = System.currentTimeMillis();
            for (World world : worlds) {
                UUID id = world.getUID();
                baseSystemTimes.put(id, sysTime);
                baseWorldTimes.put(id, world.getTime());
            }

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    long currentSystemTime = System.currentTimeMillis();
                    for (World world : worlds) {
                        try {
                            UUID id = world.getUID();
                            long ticksDelta = toMCInterval(currentSystemTime - baseSystemTimes.get(id));
                            long worldTime = baseWorldTimes.get(id) + ticksDelta;
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
            task.runTaskTimer(this, 0L, 25L * 20);
            // task.runTaskTimer(this, 0L, 10L * 20);
            logger.info("Task started successfully.");
        } catch (IllegalStateException e) {
            logger.info("Task already started.");
        }
    }

    private void stopTask() {
        if (task == null) return;
        if (!task.isCancelled()) {
            task.cancel();
            logger.info("Task stopped successfully.");
        } else {
            logger.info("Task already stopped.");
        }
    }

    private void setTimeBase(World world, long time) {
        long sysTime = System.currentTimeMillis();
        UUID id = world.getUID();
        baseSystemTimes.put(id, sysTime);
        baseWorldTimes.put(id, time);
        world.setTime(time);
    }

    private void setTimeBase(List<World> worlds, long time) {
        long sysTime = System.currentTimeMillis();
        for (World world : worlds) {
            UUID id = world.getUID();
            baseSystemTimes.put(id, sysTime);
            baseWorldTimes.put(id, time);
            world.setTime(time);
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
            startTask();
        } else {
            stopTask();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName();
        if (commandName.equals("yeyadaytime")) {
            if (args.length == 0) return false;
        
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
        } else if (commandName.equals("ytime")) {
            if (args.length == 0) {
                sender.sendMessage("Time querying not yet implemented.");
                return true;
            } else {
                long ticks;
                try {
                    ticks = parseTimeString(args[0]);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                if (args.length == 1) {
                    if (sender instanceof Player) {
                        setTimeBase(
                            ((Player)sender).getWorld(),
                            ticks
                        );
                    } else {
                        sender.sendMessage("Non-players must specify target world.");
                    }
                } else {
                    if (args[1].equals("all")) {
                        setTimeBase(Bukkit.getWorlds(), ticks);
                    } else {
                        World world = Bukkit.getWorld(args[1]);
                        if (world == null) {
                            sender.sendMessage(String.format("World '%s' not found.", args[1]));
                            return true;
                        }
                        setTimeBase(world, ticks);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
        getCommand("yeyadaytime").setExecutor(this);
        getCommand("ytime").setExecutor(this);
        saveDefaultConfig();

        configChanged();

        logger.info("YeyaDaytime ready.");
    }
}
