package cn.paper_card.little_skin_login;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.little_skin_login.api.BindingInfo;
import cn.paper_card.little_skin_login.api.BindingService;
import cn.paper_card.little_skin_login.api.exception.LittleSkinHasBeenBoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class BindingServiceImpl implements BindingService {

    private final DatabaseApi.MySqlConnection mySqlConnection;
    private Connection connection = null;
    private BindingTable table = null;


    BindingServiceImpl(@NotNull DatabaseApi.MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    private @NotNull BindingTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new BindingTable(newCon, "little_skin_bind");
        this.connection = newCon;

        return this.table;
    }


    void destroy() throws SQLException {
        synchronized (this.mySqlConnection) {
            final BindingTable t = this.table;

            if (t == null) {
                this.connection = null;
                return;
            }

            this.table = null;
            this.connection = null;
            t.close();
        }
    }

    @Override
    public boolean addBinding(@NotNull BindingInfo bindingInfo) throws LittleSkinHasBeenBoundException, SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindingTable t = this.getTable();

                // 检查是否被绑定了
                final BindingInfo info = t.queryByLittleSkinUuid(bindingInfo.littleSkinUuid());
                this.mySqlConnection.setLastUseTime();

                if (info != null)
                    throw new LittleSkinHasBeenBoundException(info, "littleSkin (%s) 已经被 %s (%s) 绑定！"
                            .formatted(info.littleSkinUuid().toString(), info.name(), info.mojangUuid().toString()));

                final int inserted = t.insert(bindingInfo);
                this.mySqlConnection.setLastUseTime();

                if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));

                return true;
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
    public boolean removeBinding(@NotNull UUID mojang, @NotNull UUID littleSkin) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindingTable t = this.getTable();

                final int deleted = t.delete(mojang, littleSkin);
                this.mySqlConnection.setLastUseTime();

                if (deleted == 1) return true;
                if (deleted == 0) return false;

                throw new RuntimeException("删除了%d条数据！".formatted(deleted));

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
    public @Nullable BindingInfo queryByLittleSkinUuid(@NotNull UUID uuid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindingTable t = this.getTable();

                final BindingInfo info = t.queryByLittleSkinUuid(uuid);
                this.mySqlConnection.setLastUseTime();

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
    public @NotNull List<BindingInfo> queryByMojangUuid(@NotNull UUID uuid, int limit, int offset) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindingTable t = this.getTable();

                final List<BindingInfo> list = t.queryByMojangUuid(uuid, limit, offset);
                this.mySqlConnection.setLastUseTime();

                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }
}
