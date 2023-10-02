package cn.paper_card.little_skin_login;

import cn.paper_card.MinecraftSessionService;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface LittleSkinLoginApi {

    record BindCodeInfo(
            int code,
            UUID uuid,
            String name,
            long time
    ) {
    }

    // 添加或更新绑定
    boolean addOrUpdateBind(@NotNull UUID mojangUuid, @NotNull UUID lSkinUuid) throws Exception;

    // 根据LittleSkinUuid查询正版UUID
    @Nullable UUID queryMojangUuid(@NotNull UUID littleSkinUuid) throws Exception;


    @SuppressWarnings("unused")
    // 根据正版UUID查询LittleSkinUuid
    @Nullable UUID queryLSkinUuid(@NotNull UUID mojangUuid) throws Exception;

    // 生成绑定验证码
    int createBindCode(@NotNull UUID lSkinUuid, @NotNull String name) throws Exception;

    @Nullable BindCodeInfo takeBindCode(int code) throws Exception;

    @SuppressWarnings("unused")
    void onPreLoginCheckNotBind(@NotNull AsyncPlayerPreLoginEvent event);

    @SuppressWarnings("unused")
    @NotNull MinecraftSessionService getSessionService();

    @SuppressWarnings("unused")
    @Nullable String[] onMainGroupMessage(@NotNull String message, long senderQq);
}
