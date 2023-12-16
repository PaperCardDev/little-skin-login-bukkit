package cn.paper_card.little_skin_login;


import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.little_skin_login.api.*;
import cn.paper_card.little_skin_login.api.exception.LittleSkinHasBeenBoundException;
import cn.paper_card.paper_card_auth.api.MinecraftSessionService;
import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class LittleSkinLoginApiImpl implements LittleSkinLoginApi {
    private final @NotNull BindingCodeServiceImpl bindCodeService;
    private final @NotNull BindingServiceImpl bindService;
    private final @NotNull TheSessionService onlineSessionService;

    private final @NotNull Logger logger;

    private final @NotNull Supplier<QqBindApi> qqBindApi;

    public LittleSkinLoginApiImpl(
            @NotNull DatabaseApi.MySqlConnection important,
            @NotNull DatabaseApi.MySqlConnection unimportant,
            @NotNull Logger logger,
            @NotNull Supplier<QqBindApi> qqBindApi) {
        this.logger = logger;
        this.bindCodeService = new BindingCodeServiceImpl(unimportant);
        this.bindService = new BindingServiceImpl(important);

        this.onlineSessionService = new TheSessionService(this.bindService, logger);
        this.qqBindApi = qqBindApi;
    }

    private void kickWhenException(@NotNull AsyncPlayerPreLoginEvent event, @NotNull Throwable t) {
        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ LittleSkin 绑定 | 错误]").color(NamedTextColor.DARK_RED));

        for (Throwable e = t; e != null; e = e.getCause()) {
            text.appendNewline();
            text.append(Component.text(e.toString()).color(NamedTextColor.RED));
        }

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        event.kickMessage(text.build());
    }


    private void onPreLoginCheckNotBind0(@NotNull AsyncPlayerPreLoginEvent event) {

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
                    this.kickWhenException(event, new Exception("%s 是一个不合法的UUID！".formatted(value), e));
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
            code = this.getBindingCodeServiceImpl().createCode(uuidLittleSkin, nameLittleSkin);
        } catch (SQLException e) {
            this.kickWhenException(event, e);
            return;
        }

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);

        event.kickMessage(Component.text()
                .append(Component.text("[ LittleSkin外置登录 ]").color(NamedTextColor.DARK_AQUA))

                .appendNewline()
                .append(Component.text("该LittleSkin角色需要绑定一个现有的正版角色").color(NamedTextColor.RED))

                .appendNewline()
                .append(Component.text("LittleSkin绑定验证码：").color(NamedTextColor.GREEN))
                .append(Component.text(code).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))

                .appendNewline()
                .append(Component.text("使用方法：直接在QQ群里发送该数字验证码"))

                .appendNewline()
                .append(Component.text("如果QQ机器人在线，会自动处理验证码"))

                .appendNewline()
                .append(Component.text("LittleSkin角色：%s (%s)".formatted(nameLittleSkin, uuidLittleSkin.toString())).color(NamedTextColor.GRAY))

                .build());
    }

    @NotNull BindingServiceImpl getBindingServiceImpl() {
        return this.bindService;
    }

    @Override
    public @NotNull BindingService getBindingService() {
        return this.bindService;
    }

    @NotNull BindingCodeServiceImpl getBindingCodeServiceImpl() {
        return this.bindCodeService;
    }

    @Override
    public @NotNull BindingCodeService getBindingCodeService() {
        return this.bindCodeService;
    }

    @Override
    public void onPreLoginCheckNotBind(@NotNull Object o) {
        final AsyncPlayerPreLoginEvent event = (AsyncPlayerPreLoginEvent) o;
        this.onPreLoginCheckNotBind0(event);
    }

    @Override
    public @NotNull Object getSessionService() {
        return this.onlineSessionService;
    }

    @Override
    public @Nullable String onMainGroupMessage(@NotNull String message, long senderQq) {

        // 没有绑定验证码要处理
        if (this.getBindingCodeServiceImpl().getCount() <= 0) return null;

        // 清理过期的验证码
        final int removed;

        try {
            removed = this.getBindingCodeServiceImpl().clearOutdated();
        } catch (SQLException e) {
            this.logger.error("binding service -> clear outdated", e);
            return "异常：%s".formatted(e.toString());
        }

        if (removed > 0) this.logger.info("清理了%d个过期的验证码".formatted(removed));

        final int code; // 绑定验证码

        try {
            code = Integer.parseInt(message);
        } catch (NumberFormatException ignored) {
            return null;
        }

        final BindingCodeInfo codeInfo;

        // 取出验证码
        try {
            codeInfo = this.getBindingCodeServiceImpl().takeCode(code);
        } catch (SQLException e) {
            this.logger.error("binding code service -> take code", e);
            return "异常：%s".formatted(e.toString());
        }

        if (codeInfo == null) {
            return "不存在或已过期失效的LittleSkin绑定验证码：%d\n请尝试重新连接获取新的验证码~".formatted(code);
        }

        // 查询QQ绑定
        final QqBindApi api = this.qqBindApi.get();

        if (api == null) {
            return "无法连接到%s，请安装QqBind插件！".formatted(QqBindApi.class.getSimpleName());
        }

        final BindInfo qqBind;

        // 查询QQ对应的正版UUID
        try {
            qqBind = api.getBindService().queryByQq(senderQq);
        } catch (Exception e) {
            this.logger.error("qq bind service -> query by qq", e);
            return "异常：%s".formatted(e.toString());
        }

        if (qqBind == null) {
            return "你的QQ还没有绑定正版账号哦\n请先使用绑定QQ~";
        }

        final BindingInfo bindingInfo = new BindingInfo(qqBind.uuid(), qqBind.name(), codeInfo.uuid(),
                "LittleSkin验证码绑定，角色名：%s".formatted(codeInfo.name()),
                System.currentTimeMillis()
        );

        try {
            this.getBindingServiceImpl().addBinding(bindingInfo);
        } catch (LittleSkinHasBeenBoundException e) {
            final BindingInfo i = e.getBindingInfo();

            return """
                    这个LittleSkin角色已经绑定到了：
                    %s (%s)
                    请使用其他角色，如需解绑，请联系管理员""".formatted(i.name(), i.mojangUuid().toString());

        } catch (SQLException e) {
            this.logger.error("binding service -> add binding", e);
            return "异常：" + e;
        }

        return """
                添加LittleSkin角色绑定成功：
                游戏名：%s
                LittleSkin角色名：%s
                快连接服务器试试叭~""".formatted(bindingInfo.name(), codeInfo.name());
    }
}
