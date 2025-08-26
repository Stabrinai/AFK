package com.github.stabrinai.afkplugin.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.stabrinai.afkplugin.settings.Settings;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class PlayerManager implements com.github.stabrinai.afkplugin.api.PlayerManager {
    HashMap<UUID, Long> afkPlayerList = new HashMap<>();
    HashMap<UUID, Long> lastActivity = new HashMap<>();

    Settings settings;

    public PlayerManager(Settings settings) {
        this.settings = settings;
    }


    @Override
    public void addAfkPlayer(Player player) {
        if (player == null) return;
        if (player.hasPermission("afk.bypass")) return;

        if (afkPlayerList.containsKey(player.getUniqueId())) return;

        afkPlayerList.put(player.getUniqueId(), System.currentTimeMillis());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == player)
                sendRichMessage(player, settings.getMsgAfkSelf());
            else
                sendRichMessage(p, settings.getMsgAfkBroadcast().replace("%player%", player.getName()));
        }

        if (settings.isAfkGodMode()) player.setInvulnerable(true);
    }

    @Override
    public void removeAfkPlayer(Player player) {
        if (player == null) return;

        if (!afkPlayerList.containsKey(player.getUniqueId())) return;

        afkPlayerList.remove(player.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == player)
                sendRichMessage(player, settings.getMsgBackSelf());
            else
                sendRichMessage(p, settings.getMsgBackBroadcast().replace("%player%", player.getName()));
        }

        if (settings.isAfkGodMode()) player.setInvulnerable(false);

        // Sync players that this player is currently tracking
        player.getTrackedBy().forEach(tracking -> syncEntityLocation(player, tracking));
    }

    private void syncEntityLocation(Player targetPlayer, Entity entity)
    {
        com.github.retrooper.packetevents.protocol.world.Location packetEventsLocation = SpigotConversionUtil.fromBukkitLocation(entity.getLocation());
        var packetTeleport = new WrapperPlayServerEntityTeleport(entity.getEntityId(), packetEventsLocation, false);

        // Teleport player, and fix their head look since the teleport packet doesn't set the head rotation
        var playerManager = PacketEvents.getAPI().getPlayerManager();
        playerManager.sendPacket(targetPlayer, packetTeleport);
        playerManager.sendPacket(targetPlayer, new WrapperPlayServerEntityHeadLook(entity.getEntityId(), entity.getYaw()));
    }

    @Override
    public void toggleAfkPlayer(Player player) {
        if (player == null) return;

        if (!isAfkPlayer(player)) {
            addAfkPlayer(player);
        } else {
            removeAfkPlayer(player);
        }
    }

    @Override
    public boolean isAfkPlayer(Player player) {
        if (player == null) return false;

        if (afkPlayerList.isEmpty()) return false;
        return afkPlayerList.containsKey(player.getUniqueId());
    }

    @Override
    public void markActive(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        if (isAfkPlayer(player)) {
            removeAfkPlayer(player);
        }
    }

    @Override
    public HashMap<UUID, Long> getLastActivity() {
        return lastActivity;
    }

    public void sendRichMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendRichMessage(message);
    }
}
