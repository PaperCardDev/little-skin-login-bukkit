package cn.paper_card.little_skin_login;


import cn.paper_card.MinecraftSessionService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Logger;

class TheSessionService extends MinecraftSessionService {

    public final static String KEY_LITTLE_SKIN_UUID = "paper-card.little-skin-uuid";
    public final static String KEY_LITTLE_SKIN_NAME = "paper-card.little-skin-name";

    private final @NotNull LittleSkinLogin plugin;


    TheSessionService(@NotNull LittleSkinLogin plugin) {
        super(constantURL("https://littleskin.cn/api/yggdrasil/sessionserver"));
        this.plugin = plugin;
    }

    private @NotNull Logger getLogger() {
        return this.plugin.getLogger();
    }


    private @NotNull GameProfile errorGameProfile(@NotNull String error) {
        final GameProfile profile = MinecraftSessionService.createInvalidProfile();
        profile.getProperties().put(KEY_KICK_MESSAGE, new Property(KEY_KICK_MESSAGE, error));
        return profile;
    }

    @Override
    protected GameProfile transformProfile(@NotNull GameProfile gameProfile) {

        final UUID littleSkinUuid = gameProfile.getId();
        if (littleSkinUuid == null) return gameProfile;

        final String name = gameProfile.getName();

        getLogger().info("LittleSkin GameProfile {name: %s, uuid: %s}".formatted(name, littleSkinUuid));

        final UUID mojangUuid;

        // 根据LittleSkin的UUID查询正版的UUID
        try {
            mojangUuid = plugin.queryMojangUuid(littleSkinUuid);
        } catch (Exception e) {
            e.printStackTrace();
            return this.errorGameProfile(e.toString());
        }

        if (mojangUuid != null) { // 转换成功
            String name2 = plugin.getServer().getOfflinePlayer(mojangUuid).getName();
            if (name2 == null) name2 = name;
            return new GameProfile(mojangUuid, name2);
        }

        // 没有绑定的情况

        // 尝试进行自动绑定
        // 因为经过测试，当使用LittleSkin的正版角色登录时，使用的UUID跟正版UUID是一样的
//        final PaperCardAuthApi.GameProfile profile;
//        try {
//            // 把LittleSkin的UUID当正版UUID来查
//            profile = plugin.getPaperCardAuthApi().getMojangProfileCache().queryByUuid(littleSkinUuid);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return this.errorGameProfile(e.toString());
//        }
//
//        if (profile != null) { // 可以进行自动绑定
//            // 即使是UUID相同也要添加绑定
//            boolean added;
//            try {
//                added = plugin.addOrUpdateBind(littleSkinUuid, littleSkinUuid);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return this.errorGameProfile(e.toString());
//            }
//
//            getLogger().info("自动%sLittleSkin绑定 {name: %s, uuid: %s}".formatted(added ? "添加" : "更新", name, littleSkinUuid));
//
//            // 绑定成功，QQ通知一下
//
//            return new GameProfile(littleSkinUuid, name);
//        }

        // 未绑定的情况
        final GameProfile profile1 = MinecraftSessionService.createInvalidProfile();
        profile1.getProperties().put(KEY_LITTLE_SKIN_UUID, new Property(KEY_LITTLE_SKIN_UUID, littleSkinUuid.toString()));
        profile1.getProperties().put(KEY_LITTLE_SKIN_NAME, new Property(KEY_LITTLE_SKIN_NAME, name));

        return profile1;
    }
}