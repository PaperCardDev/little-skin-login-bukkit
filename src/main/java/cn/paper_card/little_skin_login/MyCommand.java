package cn.paper_card.little_skin_login;

import cn.paper_card.little_skin_login.api.BindingInfo;
import cn.paper_card.little_skin_login.api.exception.LittleSkinHasBeenBoundException;
import cn.paper_card.mc_command.TheMcCommand;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class MyCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull ThePlugin plugin;

    protected MyCommand(@NotNull ThePlugin plugin) {
        super("little-skin");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("little-skin.command"));

        this.addSubCommand(new Add());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private @Nullable UUID parseArgPlayer(@NotNull String argPlayer) {

        try {
            return UUID.fromString(argPlayer);
        } catch (IllegalArgumentException ignored) {
        }

        for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
            final String name = offlinePlayer.getName();
            if (argPlayer.equals(name)) return offlinePlayer.getUniqueId();
        }

        return null;
    }


    class Add extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Add() {
            super("add");
            this.permission = plugin.addPermission(MyCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argPlayer = strings.length > 0 ? strings[0] : null;
            final String argLSkinUuid = strings.length > 1 ? strings[1] : null;

            if (argPlayer == null) {
                plugin.sendError(commandSender, "你必须指定参数：玩家名或UUID");
                return true;
            }

            final UUID mojangUuid = parseArgPlayer(argPlayer);

            if (mojangUuid == null) {
                plugin.sendError(commandSender, "找不到该玩家：%s".formatted(argPlayer));
                return true;
            }

            if (argLSkinUuid == null) {
                plugin.sendError(commandSender, "你必须指定参数：LittleSkin的UUID");
                return true;
            }

            final UUID lSkinUuid;

            try {
                lSkinUuid = UUID.fromString(argLSkinUuid);
            } catch (IllegalArgumentException e) {
                plugin.sendError(commandSender, "不正确的LittleSkinUuid: %s".formatted(argLSkinUuid));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {


                final OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(mojangUuid);
                String name = offlinePlayer.getName();
                if (name == null) name = "null";

                final BindingInfo bindingInfo = new BindingInfo(mojangUuid, name, lSkinUuid,
                        "add指令添加，%s执行".formatted(commandSender.getName()),
                        System.currentTimeMillis()
                );

                try {
                    plugin.getLittleSkinLoginApi().getBindingServiceImpl().addBinding(bindingInfo);
                } catch (LittleSkinHasBeenBoundException e) {
                    plugin.sendWarning(commandSender, e.getMessage());
                    return;
                } catch (SQLException e) {
                    plugin.getSLF4JLogger().error("add command -> binding service -> add binding", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender);
            });


            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argPlayer = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (argPlayer.isEmpty()) list.add("<玩家名或UUID>");
                for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
                    final String name = offlinePlayer.getName();
                    if (name == null) continue;
                    if (name.startsWith(argPlayer)) list.add(name);
                }
                return list;
            }

            if (strings.length == 2) {
                final String argUuid = strings[1];
                if (argUuid.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<LittleSkin的UUID>");
                    return list;
                }
            }

            return null;
        }
    }
}
