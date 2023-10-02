package cn.paper_card.little_skin_login;

import cn.paper_card.database.DatabaseApi;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class BindService {
    private Connection connection = null;
    private UuidMapTable table = null;

    private final @NotNull LittleSkinLogin plugin;

    BindService(@NotNull LittleSkinLogin plugin) {
        this.plugin = plugin;
    }

    private @NotNull Connection getConnection() throws Exception {
        if (this.connection == null) {
            final Plugin p = this.plugin.getServer().getPluginManager().getPlugin("Database");
            if (p instanceof final DatabaseApi api) {
                this.connection = api.connectImportant().getConnection();
            } else throw new Exception("Database插件未安装！");
        }
        return this.connection;
    }

    private @NotNull UuidMapTable getTable() throws Exception {
        if (this.table == null) {
            this.table = new UuidMapTable(this.getConnection(), "little_skin_bind");
        }
        return this.table;
    }

    boolean addOrUpdate(@NotNull UUID mojangUuid, @NotNull UUID littleSkinUuid) throws Exception {
        synchronized (this) {
            final UuidMapTable t = this.getTable();

            final int updated = t.updateMyMojangUuid(mojangUuid, littleSkinUuid);

            if (updated == 0) {
                final int inserted = t.insert(mojangUuid, littleSkinUuid);
                if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                return true;
            }

            if (updated == 1) return false;

            throw new Exception("更新了%d条数据！".formatted(updated));
        }
    }

    @Nullable UUID queryMojangUuid(@NotNull UUID littleSkinUuid) throws Exception {
        synchronized (this) {
            final UuidMapTable t = this.getTable();
            final List<UUID> list = t.queryByLittleSkinUuid(littleSkinUuid);

            final int size = list.size();
            if (size == 1) return list.get(0);
            if (size == 0) return null;
            throw new Exception("根据一个UUID查询到了%d条数据！".formatted(size));
        }
    }

    @Nullable UUID queryLittleSkinUuid(@NotNull UUID mojangUuid) throws Exception {
        synchronized (this) {
            final UuidMapTable t = this.getTable();
            final List<UUID> list = t.queryByMojangUuid(mojangUuid);

            final int size = list.size();

            if (size == 0) return null;
            if (size == 1) return list.get(0);

            throw new Exception("根据一个UUID查询到了%d条数据！".formatted(size));
        }
    }

    void destroy() {
        synchronized (this) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.table = null;
            }

            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.connection = null;
            }
        }
    }
}
