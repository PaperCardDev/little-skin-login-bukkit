package cn.paper_card.little_skin_login;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.little_skin_login.api.BindingCodeInfo;
import cn.paper_card.little_skin_login.api.BindingCodeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class BindingCodeServiceImpl implements BindingCodeService {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Connection connection = null;

    private BindingCodeTable table = null;

    private final AtomicInteger count = new AtomicInteger(-1);

    private final static String TABLE_NAME = "little_skin_bind_code";

    // 验证码有效时长
    private final static long MAX_ALIVE_TIME = 2 * 60 * 1000L;


    BindingCodeServiceImpl(@NotNull DatabaseApi.MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    private @NotNull BindingCodeTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new BindingCodeTable(newCon, TABLE_NAME);
        this.connection = newCon;

        this.count.set(this.table.queryCount());

        return this.table;
    }

    void destroy() throws SQLException {
        synchronized (this.mySqlConnection) {
            final BindingCodeTable t = this.table;

            if (t == null) {
                this.connection = null;
                return;
            }

            this.connection = null;
            this.table = null;
            t.close();
        }
    }

    private int randomCode() {
        final int MIN = 1;
        final int MAX = 999999;

        return new Random().nextInt(MAX - MIN + 1) + MIN;
    }

    @Override
    public int createCode(@NotNull UUID uuid, @NotNull String name) throws SQLException {

        final int code = this.randomCode();

        synchronized (this.mySqlConnection) {
            try {
                final BindingCodeTable t = this.getTable();

                // todo 检查重复验证码
                final int updated = t.updateByUuid(uuid, code, name, System.currentTimeMillis());
                this.mySqlConnection.setLastUseTime();

                if (updated == 0) {
                    final int inserted = t.insert(code, uuid, name, System.currentTimeMillis());
                    this.count.set(t.queryCount());
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return code;
                }

                if (updated != 1) throw new RuntimeException("更新了%d条数据！".formatted(updated));

                this.count.set(t.queryCount());

                return code;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable BindingCodeInfo takeCode(int code) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindingCodeTable t = this.getTable();

                final BindingCodeInfo info = t.queryByCode(code);
                this.mySqlConnection.setLastUseTime();

                if (info == null) return null;

                final int deleted = t.deleteByCode(code);

                if (deleted != 1) throw new RuntimeException("删除了%d条数据！".formatted(deleted));

                this.count.set(t.queryCount());

                // 检查是否过期
                if (System.currentTimeMillis() > info.time() + MAX_ALIVE_TIME)
                    return null;

                return info;

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }

                throw e;
            }
        }
    }

    @Override
    public int clearOutdated() throws SQLException {
        synchronized (this.mySqlConnection) {

            try {
                final BindingCodeTable t = this.getTable();

                final long time = System.currentTimeMillis() - MAX_ALIVE_TIME;

                final int deleted = t.deleteTimeBefore(time);

                this.count.set(t.queryCount());

                return deleted;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public long getMaxAliveTime() {
        return MAX_ALIVE_TIME;
    }

    int getCount() {
        return this.count.get();
    }
}
