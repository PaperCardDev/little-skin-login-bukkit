package cn.paper_card.little_skin_login;


import cn.paper_card.little_skin_login.api.BindingInfo;
import cn.paper_card.paper_card_auth.api.GameProfileInfo;
import cn.paper_card.paper_card_auth.api.MinecraftSessionService;
import cn.paper_card.paper_card_auth.api.PaperCardAuthApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

class TheSessionService extends MinecraftSessionService {

    public final static String KEY_LITTLE_SKIN_UUID = "paper-card.little-skin-uuid";
    public final static String KEY_LITTLE_SKIN_NAME = "paper-card.little-skin-name";

    private final @NotNull BindingServiceImpl bindingService;

    private final @NotNull Logger logger;

    private final @NotNull Supplier<PaperCardAuthApi> paperCardAuthApi;

    TheSessionService(@NotNull BindingServiceImpl bindingService,
                      @NotNull Logger logger,
                      @NotNull Supplier<PaperCardAuthApi> paperCardAuthApi) {
        super(constantURL("https://littleskin.cn/api/yggdrasil/sessionserver"));
        this.bindingService = bindingService;
        this.logger = logger;
        this.paperCardAuthApi = paperCardAuthApi;
    }

    private @NotNull GameProfile errorGameProfile(@NotNull String error) {
        final GameProfile profile = MinecraftSessionService.createInvalidProfile();
        profile.getProperties().put(KEY_KICK_MESSAGE, new Property(KEY_KICK_MESSAGE, error));
        return profile;
    }

    private void addProperties(@NotNull GameProfile profile, @NotNull UUID uuid) {

        final String jsonStr;
        try {
            jsonStr = requestProperties(uuid);
        } catch (IOException e) {
            this.logger.warn("fail to request little skin properties: " + e);
            return;
        }

        final JsonObject jsonObject;
        try {
            jsonObject = this.gson.fromJson(jsonStr, JsonObject.class);
        } catch (JsonSyntaxException e) {
            this.logger.warn(e.toString());
            return;
        }

        // todo 空指针判断
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
                closeStreams(bufferedReader, inputStreamReader, inputStream);
            } catch (IOException ignored) {
            }
            connection.disconnect();
            throw e;
        }

        try {
            closeStreams(bufferedReader, inputStreamReader, inputStream);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
        connection.disconnect();

        return builder.toString();
    }

    private @Nullable GameProfile tryAutoBind(@NotNull UUID littleSkinUuid, @NotNull String name) throws Exception {

        final PaperCardAuthApi api = this.paperCardAuthApi.get();
        if (api == null) return null;

        // 因为经过测试，当使用LittleSkin的正版角色登录时，使用的UUID跟正版UUID是一样的

        final GameProfileInfo info;

        // 把LittleSkin的UUID当正版UUID来查
        try {
            info = api.getProfileCacheService().queryByUuid(littleSkinUuid);
        } catch (Exception e) {
            this.logger.error("profile cache service -> query by uuid", e);
            throw e;
        }

        if (info == null) return null;

        // 可以进行自动绑定

        // 即使是UUID相同也要添加绑定

        try {
            this.bindingService.addBinding(new BindingInfo(info.uuid(), name, littleSkinUuid,
                    "LittleSkin自动绑定（UUID碰撞）",
                    System.currentTimeMillis())
            );
        } catch (Exception e) {
            this.logger.error("binding service -> add binding", e);
            throw e;
        }

        this.logger.info("自动添加LittleSkin绑定 {name: %s, uuid: %s}".formatted(info.name(), littleSkinUuid));

        // TODO 绑定成功，QQ通知一下

        return new GameProfile(littleSkinUuid, name);
    }

    @Override
    protected GameProfile transformProfile(@NotNull GameProfile gameProfile) {

        final UUID littleSkinUuid = gameProfile.getId();
        if (littleSkinUuid == null) return gameProfile;

        final String littleSkinName = gameProfile.getName();

        this.logger.info("LittleSkin角色 {name: %s, uuid: %s}".formatted(littleSkinName, littleSkinUuid.toString()));

        // 根据LittleSkin的UUID查询正版的UUID
        final BindingInfo info;

        try {
            info = this.bindingService.queryByLittleSkinUuid(littleSkinUuid);
        } catch (SQLException e) {
            this.logger.error("bind service -> query by little skin uuid", e);
            return this.errorGameProfile(e.toString());
        }

        if (info != null) { // 转换成功
//            final GameProfile profile = new GameProfile(info.mojangUuid(), info.littleSkinName());
            final GameProfile profile = new GameProfile(info.mojangUuid(),
                    littleSkinName != null && !littleSkinName.isEmpty() ? littleSkinName : info.name());
            // 查询皮肤
            this.addProperties(profile, littleSkinUuid);
            return profile;
        }

        // 没有绑定的情况

        // 尝试进行自动绑定
        try {
            final GameProfile profile = this.tryAutoBind(littleSkinUuid, littleSkinName);
            if (profile != null) return profile;
        } catch (Exception e) {
            return this.errorGameProfile(e.toString());
        }

        // 未绑定的情况
        final GameProfile profile1 = createInvalidProfile();

        profile1.getProperties().put(KEY_LITTLE_SKIN_UUID, new Property(KEY_LITTLE_SKIN_UUID, littleSkinUuid.toString()));
        profile1.getProperties().put(KEY_LITTLE_SKIN_NAME, new Property(KEY_LITTLE_SKIN_NAME, littleSkinName));

        return profile1;
    }
}