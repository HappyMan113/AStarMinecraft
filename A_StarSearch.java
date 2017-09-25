package me.happyman.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static me.happyman.Plugin.*;

public class A_StarSearch implements CommandExecutor
{
    private static class AxeListener implements Listener
    {
        private static HashMap<Player, Block> pos2Selections = new HashMap<Player, Block>();
        private static HashMap<Player, Block> pos1Selections = new HashMap<Player, Block>();

        public AxeListener()
        {
            Bukkit.getPluginManager().registerEvents(this, getPlugin());
        }

        public static boolean hasSelections(Player p)
        {
            return pos2Selections.containsKey(p) && pos1Selections.containsKey(p);
        }

        public static Block getPos1(Player p)
        {
            return pos1Selections.get(p);
        }

        public static Block getPos2(Player p)
        {
            return pos2Selections.get(p);
        }

        @EventHandler
        public void onClickAxe(PlayerInteractEvent e)
        {
            Action action = e.getAction();
            Player p = e.getPlayer();
            if (e.getItem() != null && e.getItem().getType() == Material.GOLD_AXE &&
                    (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) &&
                    hasPermissionsForCommand(p, A_STAR_CMD) && p.getGameMode() == GameMode.CREATIVE)
            {
                e.setCancelled(true);
                HashMap<Player, Block> posToSelect = null;
                Block pos = e.getClickedBlock().getRelative(0, 1, 0);
                if (A_StarSearch.isAllowedAStarBlock(pos))
                {
                    String message = "";
                    if (action.equals(Action.RIGHT_CLICK_BLOCK))
                    {
                        message = ChatColor.GREEN + "Set destination for A*";
                        posToSelect = pos2Selections;
                    }
                    else if (action.equals(Action.LEFT_CLICK_BLOCK))
                    {
                        message = ChatColor.GREEN + "Set start location for A*";
                        posToSelect = pos1Selections;
                    }
                    if (posToSelect != null)
                    {
                        posToSelect.put(p, pos);
                        if (pos1Selections.containsKey(p) && pos2Selections.containsKey(p))
                        {
                            if (pos2Selections.get(p).getWorld() != pos1Selections.get(p).getWorld())
                            {
                                if (posToSelect == pos1Selections)
                                {
                                    pos2Selections.remove(p);
                                }
                                else
                                {
                                    pos1Selections.remove(p);
                                }
                            }
                            else if (A_StarSearch.tooFarForParkour(pos1Selections.get(p), pos2Selections.get(p)))
                            {
                                message += " (" + ChatColor.LIGHT_PURPLE + "too far for parkour " + ChatColor.GREEN + ")";
                            }
                        }
                    }
                    else
                    {
                        message = ChatColor.RED + "Internal error has occurred";
                    }
                    p.sendMessage(message + ".");
                }
                else
                {
                    p.sendMessage(ChatColor.GRAY + "You have to select somewhere that has non-solid above it.");
                }
            }
        }

    }

    private static final int BREAKPOINT_ITERATIONS = 10000;
    private static String A_STAR_CMD = "a*";

    public A_StarSearch()
    {
        new AxeListener();
        setExecutor(A_STAR_CMD, this);
    }

    private static double getEstimatedDistance(Block start, Block destination)
    {
        return Math.pow(Math.pow(start.getX() - destination.getX(), 2) + Math.pow(start.getY() - destination.getY(), 2) +Math.pow(start.getZ() - destination.getZ(), 2), 0.5d);
    }

    private static ArrayList<Block> getNeighbors(HashMap<Block, Block> blocksFrom, Block b, int height, int width, boolean allowParkour, boolean allowFlight)
    {
        return getNeighbors(blocksFrom, null, b, height, width, allowParkour, allowFlight);
    }

    private static ArrayList<Block> getNeighbors(HashMap<Block, Block> blocksFrom, HashMap<Block, Material> origMats, Block b, int height, int width, boolean allowParkour, boolean allowFlight)
    {
        ArrayList<Block> neighbors = new ArrayList<Block>();
        addPathBlock(blocksFrom, origMats, b, b.getRelative(0, 0, -1), neighbors, height, width, allowParkour, allowFlight);
        addPathBlock(blocksFrom, origMats, b, b.getRelative(0, 0, 1), neighbors, height, width, allowParkour, allowFlight);
        addPathBlock(blocksFrom, origMats, b, b.getRelative(0, -1, 0), neighbors, height, width, allowParkour, allowFlight);
        addPathBlock(blocksFrom, origMats, b, b.getRelative(0, 1, 0), neighbors, height, width, allowParkour, allowFlight);
        addPathBlock(blocksFrom, origMats, b, b.getRelative(-1, 0, 0), neighbors, height, width, allowParkour, allowFlight);
        addPathBlock(blocksFrom, origMats, b, b.getRelative(1, 0, 0), neighbors, height, width, allowParkour, allowFlight);
        return neighbors;
    }

    private static boolean isClimbable(Block b, HashMap<Block, Material> origMats)
    {
        Material m = getOrigMat(b, origMats);
        return m.isSolid() || m.equals(Material.VINE) || m.equals(Material.LADDER) || m.equals(Material.WATER);
    }

    private static Material getOrigMat(Block b, HashMap<Block, Material> origMats)
    {
        if (origMats != null && origMats.containsKey(b))
        {
            return origMats.get(b);
        }
        return b.getType();
    }

    private static void addPathBlock(HashMap<Block, Block> blocksFrom, HashMap<Block, Material> origMats, Block b, Block blockToAdd, ArrayList<Block> list, int height, int width, boolean allowParkour, boolean allowFlight)
    {
        width = width/2;
        Vector movementV = blockToAdd.getLocation().toVector().subtract(b.getLocation().toVector());

        if (!isAllowedAStarBlock(blockToAdd, origMats))
        {
            return;
        }

        if (!allowFlight && !isClimbable(blockToAdd.getRelative(0, -1, 0), origMats))
        {
            Vector d = movementV;
            Block backTrackBlock = b;
            Block blockToCheck = backTrackBlock.getRelative(0, -1, 0);
            while (blocksFrom.containsKey(backTrackBlock) && !(isClimbable(blockToCheck, origMats) && !(getOrigMat(blockToCheck, origMats).equals(Material.WATER) || getOrigMat(blockToCheck, origMats).equals(Material.VINE))))
            {
                backTrackBlock = blocksFrom.get(backTrackBlock);
                blockToCheck = backTrackBlock.getRelative(0, -1, 0);
            }
            d = d.add(b.getLocation().toVector().subtract(backTrackBlock.getLocation().toVector()));
            double dx = Math.sqrt(d.getX()*d.getX() + d.getZ()*d.getZ()) - 5;
            if (dx < 0)
            {
                dx = 0;
            }
            if (d.getBlockY() > 1
                    || allowParkour && (d.getY() <= -24 || d.getY() >= 0 && Math.sqrt(d.getX()*d.getX() + d.getY()*d.getY() + d.getZ()*d.getZ()) > 5 || d.getY() < 0 && dx/-d.getY() > .5)
                    || !allowParkour && (d.getBlockY() > 0 && (d.getBlockX() > 0 || d.getBlockZ() > 0) || d.getBlockY() < 0 || d.getBlockY() == 0 && (d.getBlockX() > 1 || d.getBlockZ() > 1 || d.getBlockX() == 1 && d.getBlockZ() == 1)
                    || !(isAllowedAStarBlock(blockToAdd.getRelative(1, 0, 0), origMats) && isClimbable(blockToAdd.getRelative(1, -1, 0), origMats)
                    || isAllowedAStarBlock(blockToAdd.getRelative(-1, 0, 0), origMats) && isClimbable(blockToAdd.getRelative(-1, -1, 0), origMats)
                    || isAllowedAStarBlock(blockToAdd.getRelative(0, 0, 1), origMats) && isClimbable(blockToAdd.getRelative(0, -1, 1), origMats)
                    || isAllowedAStarBlock(blockToAdd.getRelative(0, 0, -1), origMats) && isClimbable(blockToAdd.getRelative(0, -1, -1), origMats))
            )
                    )
            {
                return;
            }
        }

        if (movementV.getBlockY() != 0)
        {
            Vector centerBlockToCheckLocation;
            if (movementV.getBlockY() > 0)
            {
                centerBlockToCheckLocation = movementV.multiply(height);
            }
            else
            {
                centerBlockToCheckLocation = movementV;
            }
            Block baseBlockToCheck = b.getRelative(centerBlockToCheckLocation.getBlockX(), centerBlockToCheckLocation.getBlockY(), centerBlockToCheckLocation.getBlockZ());
            for (int i = -width; i <= width; i++)
            {
                for (int k = -width; k <= width; k++)
                {
                    if (!isAllowedAStarBlock(baseBlockToCheck.getRelative(i, 0, k), origMats))
                    {
                        return;
                    }
                }
            }
        }
        else
        {
            Vector centerBlockToCheckLocation = movementV.multiply(width + 1);
            Block baseBlockToCheck = b.getRelative(centerBlockToCheckLocation.getBlockX(), centerBlockToCheckLocation.getBlockY(), centerBlockToCheckLocation.getBlockZ());
            if (movementV.getBlockX() != 0)
            {
                for (int j = 0; j < height; j++)
                {
                    for (int k = -width; k <= width; k++)
                    {
                        if (!isAllowedAStarBlock(baseBlockToCheck.getRelative(0, j, k), origMats))
                        {
                            return;
                        }
                    }
                }
            }
            else
            {
                for (int j = 0; j < height; j++)
                {
                    for (int i = -width; i <= width; i++)
                    {
                        if (!isAllowedAStarBlock(baseBlockToCheck.getRelative(i, j, 0), origMats))
                        {
                            return;
                        }
                    }
                }
            }
        }
        list.add(blockToAdd);
    }

    public static boolean tooFarForParkour(Block start, Block destination)
    {
        return  (Math.abs(start.getY() - destination.getY()) + 5)*Math.sqrt(Math.pow(start.getX() - destination.getX(), 2) + Math.pow(start.getZ() - destination.getZ(), 2)) > 400;
    }

    public static ArrayList<Block> findShortestPath(Block start, Block destination, int height, int width)
    {
        if (tooFarForParkour(start, destination) || start.getY() == destination.getY())
        {
            return findShortestPath(start, destination, height, width, false, false);
        }
        else
        {
            return findShortestPath(start, destination, height, width, true, false);
        }
    }

    public void showAStarAlgorithm(final CommandSender commandSender, final Block start, final Block destination, final int height, final int width)
    {
        if (tooFarForParkour(start, destination) || start.getY() == destination.getY())
        {
            showAStarAlgorithm(commandSender, start, destination, height, width, false, false);
        }
        else
        {
            showAStarAlgorithm(commandSender, start, destination, height, width, true, false);
        }
    }


    //A*
    public static ArrayList<Block> findShortestPath(Block start, Block destination, int height, int width, boolean allowParkour, boolean allowFlight)
    {
        if (start.getWorld() != destination.getWorld())
        {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Error! Please use shortest path correctly!");
            return null;
        }

        HashMap<Block, Integer> gScore = new HashMap<Block, Integer>();
        gScore.put(start, 0);

        HashMap<Block, Double> fScore = new HashMap<Block, Double>();
        fScore.put(start, getEstimatedDistance(start, destination));

        HashMap<Block, Block> blocksFrom = new HashMap<Block, Block>();

        ArrayList<Block> openSet = new ArrayList<Block>();
        openSet.add(start);

        ArrayList<Block> closedSet = new ArrayList<Block>();

        int max_iterations = (int)(Math.pow(2*getEstimatedDistance(start, destination), 3));
        if (max_iterations > BREAKPOINT_ITERATIONS)
        {
            max_iterations = BREAKPOINT_ITERATIONS;
        }

        for (int i = 0; i < max_iterations && !openSet.isEmpty(); i++)
        {
            Block currentBlock = null;
            double min = -1;
            for (Block b : openSet)
            {
                double distanceBToDest = fScore.get(b);
                if (currentBlock == null || distanceBToDest < min)
                {
                    min = distanceBToDest;
                    currentBlock = b;
                }
            }

            if (getEstimatedDistance(currentBlock, destination) < 0.01)
            {
                ArrayList<Block> path = new ArrayList<Block>();
                path.add(currentBlock);
                int maxIt = blocksFrom.size();
                for (int j = 0; j < maxIt && !blocksFrom.isEmpty(); j++)
                {
                    if (blocksFrom.containsKey(currentBlock))
                    {
                        Block tempBlock = currentBlock;
                        currentBlock = blocksFrom.get(currentBlock);
                        blocksFrom.remove(tempBlock);
                        path.add(0, currentBlock);
                    }
                }
                if (path.isEmpty())
                {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Major error! Could not find path!");
                }
                return path;
            }

            openSet.remove(currentBlock);
            closedSet.add(currentBlock);

            ArrayList<Block> neighbors = getNeighbors(blocksFrom, currentBlock, height, width, allowParkour, allowFlight);
            for (Block neighbor : neighbors)
            {
                if (!closedSet.contains(neighbor))
                {
                    boolean newlyDiscovered = false;
                    if (!openSet.contains(neighbor))
                    {
                        openSet.add(neighbor);
                        newlyDiscovered = true;
                    }

                    int tentativeGScore = gScore.get(currentBlock) + 1;
                    if (newlyDiscovered || tentativeGScore < gScore.get(neighbor))
                    {
                        blocksFrom.put(neighbor, currentBlock);
                        gScore.put(neighbor, tentativeGScore);
                        fScore.put(neighbor, tentativeGScore + getEstimatedDistance(neighbor, destination));
                    }
                }
            }
        }
        return null;
    }

    //A*
    public static void showAStarAlgorithm(final CommandSender commandSender, final Block start, final Block destination, final int height, final int width, final boolean allowParkour, final boolean allowFlight)
    {
        if (start.getWorld() != destination.getWorld())
        {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Error! Please use shortest path correctly!");
            return;
        }

        final HashMap<Block, Material> origMats = new HashMap<Block, Material>();
        origMats.put(start, start.getType());

        final HashMap<Block, Integer> gScore = new HashMap<Block, Integer>();
        final HashMap<Block, Double> fScore = new HashMap<Block, Double>();
        final HashMap<Block, Block> blocksFrom = new HashMap<Block, Block>();
        final ArrayList<Block> openSet = new ArrayList<Block>();
        final ArrayList<Block> closedSet = new ArrayList<Block>();
        openSet.add(start);

        gScore.put(start, 0);
        final int distance = (int)getEstimatedDistance(start, destination);
        fScore.put(start, getEstimatedDistance(start, destination));
        int maxIt = (int)(Math.pow(2*distance, 3))+1;
        if (maxIt > BREAKPOINT_ITERATIONS)
        {
            maxIt = BREAKPOINT_ITERATIONS;
        }
        final int max_iterations = maxIt;
        final ArrayList<Block> returnResult = new ArrayList<Block>();

        final int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable()
        {
            int i = 0;
            Block prev = null;

            public void run()
            {
                if (i < max_iterations && !openSet.isEmpty())
                {
                    Block currentBlock = null;
                    double min = -1;
                    for (Block b : openSet)
                    {
                        double distanceBToDest = fScore.get(b);
                        if (currentBlock == null || distanceBToDest < min)
                        {
                            min = distanceBToDest;
                            currentBlock = b;
                        }
                    }
                    currentBlock.setType(Material.LAPIS_BLOCK);
                    if (prev != null)
                    {
                        prev.setType(Material.STAINED_GLASS);
                    }

                    if (getEstimatedDistance(currentBlock, destination) < 0.01)
                    {
                        returnResult.add(currentBlock);
                        for (int j = 0; j < max_iterations && !blocksFrom.isEmpty(); j++)
                        {
                            if (blocksFrom.containsKey(currentBlock))
                            {
                                Block tempBlock = currentBlock;
                                currentBlock = blocksFrom.get(currentBlock);
                                blocksFrom.remove(tempBlock);
                                returnResult.add(0, currentBlock);
                            }
                        }
                        if (returnResult.isEmpty())
                        {
                            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Major error! Could not find path!");
                        }
                        Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable()
                        {
                            public void run()
                            {
                                for (Block b : origMats.keySet())
                                {
                                    b.setType(origMats.get(b));
                                }
                                outputBlockPath(commandSender, returnResult);
                            }
                        }, 3);
                        i = max_iterations + 1;
                    }
                    else
                    {
                        openSet.remove(currentBlock);
                        closedSet.add(currentBlock);

                        ArrayList<Block> neighbors = getNeighbors(blocksFrom, origMats, currentBlock, height, width, allowParkour, allowFlight);
                        for (Block neighbor : neighbors)
                        {
                            if (!closedSet.contains(neighbor))
                            {
                                boolean newlyDiscovered = false;
                                if (!openSet.contains(neighbor))
                                {
                                    openSet.add(neighbor);
                                    if (!origMats.containsKey(neighbor) && (neighbor.getType().equals(Material.GLASS) || neighbor.getType().equals(Material.STAINED_GLASS)))
                                    {
                                        origMats.put(neighbor, Material.AIR);
                                    }
                                    else
                                    {
                                        origMats.put(neighbor, neighbor.getType());
                                    }
                                    neighbor.setType(Material.GLASS);
                                    newlyDiscovered = true;
                                }

                                int tentativeGScore = gScore.get(currentBlock) + 1;
                                if (newlyDiscovered || tentativeGScore < gScore.get(neighbor))
                                {
                                    blocksFrom.put(neighbor, currentBlock);
                                    gScore.put(neighbor, tentativeGScore);
                                    fScore.put(neighbor, tentativeGScore + getEstimatedDistance(neighbor, destination));
                                }
                            }
                            i++;
                        }
                    }
                    prev = currentBlock;
                }
                else if (i == max_iterations || i < max_iterations && openSet.isEmpty())
                {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Error! Could not find path in time!");
                    if (openSet.isEmpty())
                    {
                        commandSender.sendMessage(ChatColor.RED + "Could not find route between those 2 points!");
                    }
                    else
                    {
                        commandSender.sendMessage(ChatColor.RED + "Search took too long!");
                    }
                    for (Block b : origMats.keySet())
                    {
                        b.setType(origMats.get(b));
                    }
                    i = max_iterations + 1;
                }
            }
        }, 0, 1);
        Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable()
        {
            public void run()
            {
                Bukkit.getScheduler().cancelTask(task);
            }
        }, (max_iterations+3)*1);
    }

    private static void outputBlockPath(CommandSender commandSender, final ArrayList<Block> returnResult)
    {
        if (returnResult != null)
        {
            commandSender.sendMessage(ChatColor.GREEN + "Outputting path");
            int delay = 5;
            final int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable()
            {
                private int cur = 0;
                Block prevBlock = null;
                Material prevMat = null;
                public void run()
                {
                    if (cur < returnResult.size())
                    {
                        if (cur > 0)
                        {
                            prevBlock.setType(prevMat);
                        }

                        final Block b = returnResult.get(cur);
                        prevMat = b.getType();
                        prevBlock = b;

                        Bukkit.getScheduler().callSyncMethod(getPlugin(), new Callable()
                        {
                            public String call()
                            {
                                b.setType(Material.REDSTONE_BLOCK);
                                return "";
                            }
                        });
                        cur++;
                    }
                    else if (cur == returnResult.size())
                    {
                        if (prevBlock != null)
                        {
                            prevBlock.setType(prevMat);
                        }
                    }
                }
            }, 0, delay);

            Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable()
            {
                public void run()
                {
                    Bukkit.getScheduler().cancelTask(task);
                }
            }, (returnResult.size() + 3)*delay);
        }
        else
        {
            commandSender.sendMessage(ChatColor.RED + "Could not find a valid path!");
        }
    }

    public static boolean isAllowedAStarBlock(Block b)
    {
        return isAllowedAStarBlock(b, null);
    }

    private static boolean isAllowedAStarBlock(Block b, HashMap<Block, Material> origMats)
    {
        Material m = getOrigMat(b, origMats);
        return !m.isSolid() && !m.equals(Material.LAVA) && !b.getType().equals(Material.FIRE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args)
    {
        if (cmd.getName().equals(A_STAR_CMD))
        {
            if (!(sender instanceof Player))
            {
                sender.sendMessage(ChatColor.RED + "You must be in-game noob!");
            }
            else
            {
                Player p = (Player)sender;
                if (!AxeListener.hasSelections(p))
                {
                    p.sendMessage(ChatColor.GRAY + "You must select 2 locations with a golden axe before you can run A*.");
                }
                else
                {
                    Block start = AxeListener.getPos1(p);
                    Block destination = AxeListener.getPos2(p);
                    int height = 2;
                    int width = 1;
                    if (args.length > 0)
                    {
                        try
                        {
                            if (args.length < 2)
                            {
                                throw new NumberFormatException();
                            }
                            height = Integer.valueOf(args[0]);
                            width =  Integer.valueOf(args[1]);
                        }
                        catch (NumberFormatException e)
                        {
                            return false;
                        }
                    }
                    if (args.length > 2)
                    {
                        boolean allowParkour = args[2].equalsIgnoreCase("true");
                        if (!allowParkour && !args[2].equalsIgnoreCase("false"))
                        {
                            return false;
                        }
                        boolean allowFlight = false;
                        if (args.length > 3)
                        {
                            allowFlight = args[3].equalsIgnoreCase("true");
                            if (!allowFlight && !args[3].equalsIgnoreCase("false"))
                            {
                                return false;
                            }
                        }
                        showAStarAlgorithm(p, start, destination, height, width, allowParkour, allowFlight);
                        //outputBlockPath(p, findShortestPath(start, destination, height, width, allowParkour, allowFlight));
                    }
                    else
                    {
                        showAStarAlgorithm(p, start, destination, height, width);
                        //outputBlockPath(p, findShortestPath(start, destination, height, width));
                    }
                }
            }
            return true;
        }
        return false;
    }
}
