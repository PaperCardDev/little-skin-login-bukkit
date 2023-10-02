package cn.paper_card.little_skin_login;

import cn.paper_card.MinecraftSessionService;
import cn.paper_card.player_qq_bind.QqBindApi;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public final class LittleSkinLogin extends JavaPlugin implements LittleSkinLoginApi {

    private QqBindApi qqBindApi = null;
    private final @NotNull BindCodeService bindCodeService;
    private final @NotNull BindService bindService;
    private final @NotNull TheSessionService onlineSessionService;

    public LittleSkinLogin() {
        this.bindCodeService = new BindCodeService(this);
        this.bindService = new BindService(this);
        this.onlineSessionService = new TheSessionService(this);
    }

    @Override
    public void onEnable() {
        final PluginCommand command = this.getCommand("little-skin");
        final MyCommand myCommand = new MyCommand(this);
        assert command != null;
        command.setExecutor(myCommand);
        command.setTabCompleter(myCommand);
    }

    @Override
    public void onDisable() {
        this.bindService.destroy();
        this.bindCodeService.destroy();
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    private @Nullable QqBindApi getQqBindApi() {
        if (this.qqBindApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerQqBind");
            if (plugin instanceof final QqBindApi api)
                this.qqBindApi = api;
        }
        return this.qqBindApi;
    }

    @Override
    public boolean addOrUpdateBind(@NotNull UUID mojangUuid, @NotNull UUID lSkinUuid) throws Exception {
        return this.bindService.addOrUpdate(mojangUuid, lSkinUuid);
    }

    @Override
    public @Nullable UUID queryMojangUuid(@NotNull UUID littleSkinUuid) throws Exception {
        return this.bindService.queryMojangUuid(littleSkinUuid);
    }

    @Override
    public @Nullable UUID queryLSkinUuid(@NotNull UUID mojangUuid) throws Exception {
        return this.bindService.queryLittleSkinUuid(mojangUuid);
    }


    @Override
    public int createBindCode(@NotNull UUID lSkinUuid, @NotNull String name) throws Exception {
        return this.bindCodeService.createBindCode(lSkinUuid, name);
    }

    @Override
    public @Nullable BindCodeInfo takeBindCode(int code) throws Exception {
        return this.bindCodeService.takeBindCode(code);
    }

    @Override
    public void onPreLoginCheckNotBind(@NotNull AsyncPlayerPreLoginEvent event) {

        final UUID id = event.getUniqueId();

        if (!id.equals(MinecraftSessionService.INVALID_UUID)) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            return;
        }

        UUID uuidLittleSkin = null;
        String nameLittleSkin = null;

        final PlayerProfile playerProfile = event.getPlayerProfile();

        final Set<ProfileProperty> properties = playerProfile.getProperties();

        for (final ProfileProperty property : properties) {

            // LittleSkin Uuid
            if (TheSessionService.KEY_LITTLE_SKIN_UUID.equals(property.getName())) {

                final String value = property.getValue();
                try {
                    uuidLittleSkin = UUID.fromString(value);
                } catch (IllegalArgumentException e) {
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                    event.kickMessage(Component.text("%s 是一个不合法的UUID！".formatted(value)));
                    return;
                }
            }

            // LittleSkin 游戏名
            if (TheSessionService.KEY_LITTLE_SKIN_NAME.equals(property.getName())) {
                nameLittleSkin = property.getValue();
            }
        }


        // 扫描不到LittleSkin相关的信息
        if (uuidLittleSkin == null || nameLittleSkin == null) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            return;
        }

        // 生成绑定验证码
        final int code;

        try {
            code = createBindCode(uuidLittleSkin, nameLittleSkin);
        } catch (Exception e) {
            e.printStackTrace();
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text(e.getMessage()));
            return;
        }

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);
        event.kickMessage(Component.text()
                .append(Component.text("[ LittleSkin外置登录 ]").color(NamedTextColor.LIGHT_PURPLE))
                .append(Component.newline())
                .append(Component.text("该LittleSkin角色需要绑定一个现有的正版角色"))
                .append(Component.newline())
                .append(Component.text("LittleSkin绑定验证码："))
                .append(Component.text(code).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("使用方法：直接在QQ群里发送该数字验证码"))
                .append(Component.newline())
                .append(Component.text("如果QQ机器人在线，会自动处理验证码"))
                .build());
    }

    @Override
    public @NotNull MinecraftSessionService getSessionService() {
        return this.onlineSessionService;
    }

    @Override
    public @Nullable String[] onMainGroupMessage(@NotNull String message, long senderQq) {

        // 没有绑定验证码要处理
        if (this.bindCodeService.getCodeCount() == 0) return null;

        // 清理过期的验证码
        final int removed;

        try {
            removed = this.bindCodeService.removeOutdatedCodes();
        } catch (Exception e) {
            e.printStackTrace();
            return new String[]{"异常：%s".formatted(e.toString())};
        }

        if (removed > 0)
            getLogger().info("清理了%d个过期的验证码".formatted(removed));

        final int code; // 绑定验证码

        try {
            code = Integer.parseInt(message);
        } catch (NumberFormatException ignored) {
            return null;
        }

        final BindCodeInfo info;

        // 取出验证码
        try {
            info = this.takeBindCode(code);
        } catch (Exception e) {
            e.printStackTrace();
            return new String[]{"异常：%s".formatted(e.toString())};
        }

        if (info == null) {
            return new String[]{"无效或过期的LittleSkin绑定验证码：%d\n请尝试重新连接获取新的验证码~".formatted(code)};
        }

        // 查询QQ绑定
        final QqBindApi api = this.getQqBindApi();

        if (api == null) {
            return new String[]{"无法获取QqBindApi，请安装PlayerQqBind插件！"};
        }

        final QqBindApi.BindInfo qqBind;

        // 查询QQ对应的正版UUID
        try {
            qqBind = api.queryByQq(senderQq);
        } catch (Exception e) {
            e.printStackTrace();
            return new String[]{"异常：%s".formatted(e.toString())};
        }

        if (qqBind == null || qqBind.uuid() == null) {
            return new String[]{"你的QQ还没有绑定正版账号哦\n请先使用正版登录方式连接、进入服务器~"};
        }

        { // 检查该LittleSkinUuid是否已经被绑定
            final UUID uuid;

            try {
                uuid = this.queryMojangUuid(info.uuid());
            } catch (Exception e) {
                e.printStackTrace();
                return new String[]{e.toString()};
            }

            if (uuid != null) {
                return new String[]{"""
                        该LittleSkin角色已经绑定了正版账号:
                        LittleSkin游戏名: %s,
                        LittleSkinUuid: %s,
                        正版UUID: %s""".formatted(info.name(), info.uuid(), uuid)
                };
            }
        }

        final boolean added;

        // 添加或更新绑定
        try {
            added = this.addOrUpdateBind(qqBind.uuid(), info.uuid());
        } catch (Exception e) {
            e.printStackTrace();
            return new String[]{e.toString()};
        }

        return new String[]{"""
                %sLittleSkin角色绑定成功:
                LittleSkin游戏名: %s,
                快连接服务器试试叭~""".formatted(
                added ? "添加" : "更新", info.name()
        )};
    }
}
