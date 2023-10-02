package cn.paper_card.little_skin_login;

import cn.paper_card.database.DatabaseApi;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

class BindCodeService {

    private final @NotNull LittleSkinLogin plugin;

    private Connection connection = null;

    private BindCodeTable table = null;

    private final static String TABLE_NAME = "little_skin_bind_code";

    // 验证码有效时长
    private final static long MAX_ALIVE_TIME = 60 * 1000L;

    private int codes = 0;

    BindCodeService(@NotNull LittleSkinLogin plugin) {
        this.plugin = plugin;
    }

    private @NotNull BindCodeTable getTable() throws Exception {
        if (this.table == null) {
            this.table = new BindCodeTable(this.getConnection(), TABLE_NAME);

            // 查询验证码数量
            try {
                this.codes = this.table.queryCount();
            } catch (SQLException e) {
                try {
                    this.table.close();
                } catch (SQLException ignored) {
                }
                this.table = null;

                throw e;
            }
        }

        return this.table;
    }

    private @NotNull Connection getConnection() throws Exception {
        if (this.connection == null) {
            final Plugin p = this.plugin.getServer().getPluginManager().getPlugin("Database");
            if (p instanceof final DatabaseApi api) {
                this.connection = api.connectUnimportant().getConnection();
            } else throw new Exception("Database插件未安装！");
        }
        return this.connection;
    }


    void destroy() {
        synchronized (this) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    this.plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.table = null;
            }

            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (SQLException e) {
                    this.plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.connection = null;
            }
        }
    }

    private int randomCode() {
        final int MIN = 1;
        final int MAX = 999999;

        return new Random().nextInt(MAX - MIN + 1) + MIN;
    }

    int createBindCode(@NotNull UUID uuid, @NotNull String name) throws Exception {
        synchronized (this) {
            final BindCodeTable t = this.getTable();
            final int code = this.randomCode();
            final long time = System.currentTimeMillis();

            final int updated = t.updateByUuid(uuid, code, name, time);
            if (updated == 0) {
                final int insert = t.insert(code, uuid, name, time);
                this.codes += insert;
                if (insert != 1) throw new Exception("插入了%d条数据！".formatted(insert));
            }
            return code;
        }
    }

    @Nullable LittleSkinLoginApi.BindCodeInfo takeBindCode(int code) throws Exception {
        synchronized (this) {
            final BindCodeTable t = this.getTable();

            final List<LittleSkinLoginApi.BindCodeInfo> list = t.queryByCode(code);

            final int size = list.size();

            if (size == 0) return null;

            if (size == 1) {
                final LittleSkinLoginApi.BindCodeInfo info = list.get(0);

                this.codes -= t.deleteByCode(code);

                final long current = System.currentTimeMillis();

                if (current < info.time() + MAX_ALIVE_TIME) { // 没有过期
                    return info;
                } else { // 过期
                    return null;
                }

            }
            throw new Exception("根据一个验证码查询到%d条数据！".formatted(size));
        }
    }

    int removeOutdatedCodes() throws Exception {
        synchronized (this) {
            final BindCodeTable t = this.getTable();
            final long current = System.currentTimeMillis();
            final int deleted = t.deleteTimeBefore(current - MAX_ALIVE_TIME);
            this.codes -= deleted;
            return deleted;
        }
    }

    int getCodeCount() {
        synchronized (this) {
            return this.codes;
        }
    }
}
