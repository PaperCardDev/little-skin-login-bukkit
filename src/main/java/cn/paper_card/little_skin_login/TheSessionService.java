package cn.paper_card.little_skin_login;


import cn.paper_card.MinecraftSessionService;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

class TheSessionService extends MinecraftSessionService {

    public final static String KEY_LITTLE_SKIN_UUID = "paper-card.little-skin-uuid";
    public final static String KEY_LITTLE_SKIN_NAME = "paper-card.little-skin-name";

    private final @NotNull LittleSkinLogin plugin;

    private final @NotNull Gson gson;


    TheSessionService(@NotNull LittleSkinLogin plugin) {
        super(constantURL("https://littleskin.cn/api/yggdrasil/sessionserver"));
        this.plugin = plugin;
        this.gson = new Gson();
    }

    private @NotNull Logger getLogger() {
        return this.plugin.getLogger();
    }


    private @NotNull GameProfile errorGameProfile(@NotNull String error) {
        final GameProfile profile = MinecraftSessionService.createInvalidProfile();
        profile.getProperties().put(KEY_KICK_MESSAGE, new Property(KEY_KICK_MESSAGE, error));
        return profile;
    }

    private static void close(@NotNull InputStream inputStream, @NotNull InputStreamReader inputStreamReader, @NotNull BufferedReader reader) throws IOException {
        IOException exception = null;

        try {
            reader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            inputStreamReader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            exception = e;
        }

        if (exception != null) throw exception;
    }

    private void addProperties(@NotNull GameProfile profile, @NotNull UUID uuid) {
        final String jsonStr;
        try {
            jsonStr = requestProperties(uuid);
        } catch (IOException e) {
            this.getLogger().warning(e.toString());
            return;
        }

        final JsonObject jsonObject;
        try {
            jsonObject = this.gson.fromJson(jsonStr, JsonObject.class);
        } catch (JsonSyntaxException e) {
            this.getLogger().warning(e.toString());
            return;
        }

        final JsonArray properties = jsonObject.get("properties").getAsJsonArray();
        for (JsonElement property : properties) {
            final JsonObject asJsonObject = property.getAsJsonObject();
            final String name = asJsonObject.get("name").getAsString();
            final String value = asJsonObject.get("value").getAsString();
            final String signature = asJsonObject.get("signature").getAsString();
            profile.getProperties().put(name, new Property(name, value, signature));
        }
    }

    private @NotNull String requestProperties(@NotNull UUID uuid) throws IOException {
        // GET /sessionserver/session/minecraft/profile/{uuid}?unsigned={unsigned}

        final URL url = constantURL("https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false");

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        final InputStream inputStream;

        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }

        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        final StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
        } catch (IOException e) {
            try {
                close(inputStream, inputStreamReader, bufferedReader);
            } catch (IOException ignored) {
            }
            connection.disconnect();
            throw e;
        }

        try {
            close(inputStream, inputStreamReader, bufferedReader);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
        connection.disconnect();

        return builder.toString();
    }


    @Override
    protected GameProfile transformProfile(@NotNull GameProfile gameProfile) {

        final UUID littleSkinUuid = gameProfile.getId();
        if (littleSkinUuid == null) return gameProfile;

        final String name = gameProfile.getName();

        getLogger().info("LittleSkin GameProfile {name: %s, uuid: %s}".formatted(name, littleSkinUuid));
        final PropertyMap properties = gameProfile.getProperties();
        final Collection<Property> properties1 = properties.get("textures");
        for (Property property : properties1) {
            getLogger().info("Name: " + property.getName());
            getLogger().info("Value: " + property.getValue());
            getLogger().info("Signature: " + property.getSignature());
        }

        final UUID mojangUuid;

        // 根据LittleSkin的UUID查询正版的UUID
        try {
            mojangUuid = plugin.queryMojangUuid(littleSkinUuid);
        } catch (Exception e) {
            e.printStackTrace();
            return this.errorGameProfile(e.toString());
        }

        if (mojangUuid != null) { // 转换成功
            final GameProfile profile = new GameProfile(mojangUuid, name);
            // 查询皮肤
            this.addProperties(profile, littleSkinUuid);
            return profile;
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