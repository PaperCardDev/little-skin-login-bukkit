package cn.paper_card.little_skin_login;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class MyCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull LittleSkinLogin plugin;

    protected MyCommand(@NotNull LittleSkinLogin plugin) {
        super("little-skin");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("little-skin.command"));

        this.addSubCommand(new SetBind());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private static void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text(error).color(NamedTextColor.DARK_RED));
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


    class SetBind extends TheMcCommand {

        private final @NotNull Permission permission;

        protected SetBind() {
            super("set-bind");
            this.permission = plugin.addPermission(MyCommand.this.permission.getName() + ".set-bind");
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
                sendError(commandSender, "你必须指定参数：玩家名或UUID");
                return true;
            }


            final UUID mojangUuid = parseArgPlayer(argPlayer);

            if (mojangUuid == null) {
                sendError(commandSender, "找不到该玩家：%s".formatted(argPlayer));
                return true;
            }

            if (argLSkinUuid == null) {
                sendError(commandSender, "你必须指定参数：LittleSkin的UUID");
                return true;
            }

            final UUID lSkinUuid;

            try {
                lSkinUuid = UUID.fromString(argLSkinUuid);
            } catch (IllegalArgumentException e) {
                sendError(commandSender, "不正确的LittleSkinUuid: %s".formatted(argLSkinUuid));
                return true;
            }

            {
                final UUID uuid;
                try {
                    uuid = plugin.queryLSkinUuid(lSkinUuid);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(commandSender, e.toString());
                    return true;
                }

                if (uuid != null) {
                    sendError(commandSender, "该LittleSkinUuid已经被绑定！");
                    return true;
                }
            }


            final boolean added;

            try {
                added = plugin.addOrUpdateBind(mojangUuid, lSkinUuid);
            } catch (Exception e) {
                e.printStackTrace();
                sendError(commandSender, e.toString());
                return true;
            }

            final OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(mojangUuid);
            String name = offlinePlayer.getName();
            if (name == null) name = offlinePlayer.getUniqueId().toString();

            commandSender.sendMessage(Component.text()
                    .append(Component.text("%s成功，已将玩家[ %s ]绑定的LittleSkinUuid设置为：".formatted(
                            added ? "添加" : "更新", name
                    )))
                    .append(Component.newline())
                    .append(Component.text(lSkinUuid.toString()))
                    .build());

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
