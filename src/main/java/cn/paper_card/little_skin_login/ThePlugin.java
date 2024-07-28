package cn.paper_card.little_skin_login;

import cn.paper_card.client.api.PaperClientApi;
import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.little_skin_login.api.LittleSkinLoginApi;
import cn.paper_card.paper_card_auth.api.PaperCardAuthApi;
import cn.paper_card.qq_bind.api.QqBindApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class ThePlugin extends JavaPlugin {

    private LittleSkinLoginApiImpl littleSkinLoginApi = null;

    private QqBindApi qqBindApi = null;

    private PaperCardAuthApi paperCardAuthApi = null;

    private final @NotNull Component prefix;

    private final @NotNull TaskScheduler taskScheduler;

    private PaperClientApi paperClientApi = null;

    public ThePlugin() {
        this.prefix = Component.text()
                .append(Component.text("["))
                .append(Component.text("LittleSkin"))
                .append(Component.text("]"))
                .build();

        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    @Override
    public void onLoad() {

        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);

        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        this.littleSkinLoginApi = new LittleSkinLoginApiImpl(
                api.getRemoteMySQL().getConnectionImportant(),
                api.getRemoteMySQL().getConnectionUnimportant(),
                this.getSLF4JLogger(),
                () -> ThePlugin.this.qqBindApi,
                () -> ThePlugin.this.paperCardAuthApi,
                () -> ThePlugin.this.paperClientApi
        );

        this.getSLF4JLogger().info("注册%s...".formatted(LittleSkinLoginApi.class.getSimpleName()));
        this.getServer().getServicesManager().register(LittleSkinLoginApi.class, this.littleSkinLoginApi, this, ServicePriority.Highest);
    }

    @Override
    public void onEnable() {
        this.qqBindApi = this.getServer().getServicesManager().load(QqBindApi.class);
        if (this.qqBindApi == null) {
            this.getSLF4JLogger().warn("无法连接到" + QqBindApi.class.getSimpleName());
        } else {
            this.getSLF4JLogger().info("已经连接到" + QqBindApi.class.getSimpleName());
        }

        this.paperCardAuthApi = this.getServer().getServicesManager().load(PaperCardAuthApi.class);
        if (this.paperCardAuthApi == null) {
            this.getSLF4JLogger().warn("无法连接到" + PaperCardAuthApi.class.getSimpleName());
        } else {
            this.getSLF4JLogger().info("已经连接到" + PaperCardAuthApi.class.getSimpleName());
        }

        this.paperClientApi = this.getServer().getServicesManager().load(PaperClientApi.class);
        if (this.paperClientApi == null) {
            this.getSLF4JLogger().warn("无法连接到" + PaperClientApi.class.getSimpleName());
        } else {
            this.getSLF4JLogger().info("已经连接到" + PaperClientApi.class.getSimpleName());
        }


        new MyCommand(this);
    }

    @Override
    public void onDisable() {
        this.getServer().getServicesManager().unregisterAll(this);

        if (this.littleSkinLoginApi != null) {
            try {
                this.littleSkinLoginApi.getBindingServiceImpl().destroy();
            } catch (SQLException e) {
                this.getSLF4JLogger().error("destroy binding service", e);
            }

            try {
                this.littleSkinLoginApi.getBindingCodeServiceImpl().destroy();
            } catch (SQLException e) {
                this.getSLF4JLogger().error("destroy binding code service", e);
            }
        }


        this.taskScheduler.cancelTasks(this);
    }

    @NotNull LittleSkinLoginApiImpl getLittleSkinLoginApi() {
        return this.littleSkinLoginApi;
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();
        text.append(this.prefix);
        text.appendSpace();
        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(warning).color(NamedTextColor.YELLOW))
                .build());
    }

    void sendInfo(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text("添加LittleSkin绑定成功").color(NamedTextColor.YELLOW))
                .build());
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }
}
