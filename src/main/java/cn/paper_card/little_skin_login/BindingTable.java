package cn.paper_card.little_skin_login;

import cn.paper_card.database.api.Util;
import cn.paper_card.little_skin_login.api.BindingInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

class BindingTable {

    private PreparedStatement statementInsert = null;

    private PreparedStatement statementDelete = null;

    private PreparedStatement statementQueryByLSkinUuid = null;

    private PreparedStatement statementQueryByMojangUuid = null;

    private final @NotNull String name;
    private final @NotNull Connection connection;

    private final static String COLS = "m_uid1, m_uid2, m_name, l_uid1, l_uid2, remark, time";

    BindingTable(@NotNull Connection connection, @NotNull String name) throws SQLException {
        this.name = name;
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    m_uid1 BIGINT NOT NULL,
                    m_uid2 BIGINT NOT NULL,
                    m_name VARCHAR(64) NOT NULL,
                    l_uid1 BIGINT NOT NULL,
                    l_uid2 BIGINT NOT NULL,
                    remark VARCHAR(128) NOT NULL,
                    time BIGINT NOT NULL,
                    PRIMARY KEY(m_uid1, m_uid2)
                )""".formatted(this.name));
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }


    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement
                    ("INSERT INTO %s (m_uid1, m_uid2, m_name, l_uid1, l_uid2, remark, time) VALUES (?, ?, ?, ?, ?, ?, ?)".formatted(this.name));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementDelete() throws SQLException {
        if (this.statementDelete == null) {
            this.statementDelete = this.connection.prepareStatement
                    ("DELETE FROM %s WHERE m_uid1=? AND m_uid2=? AND l_uid1=? AND l_uid2=? LIMIT 1".formatted(this.name));
        }
        return this.statementDelete;
    }

    private @NotNull PreparedStatement getStatementQueryByLSkinUuid() throws SQLException {
        if (this.statementQueryByLSkinUuid == null) {
            this.statementQueryByLSkinUuid = this.connection.prepareStatement
                    ("SELECT %s FROM %s WHERE l_uid1=? AND l_uid2=? LIMIT 1".formatted(COLS, this.name));
        }

        return this.statementQueryByLSkinUuid;
    }

    private @NotNull PreparedStatement getStatementQueryByMojangUuid() throws SQLException {
        if (this.statementQueryByMojangUuid == null) {
            this.statementQueryByMojangUuid = this.connection.prepareStatement
                    ("SELECT %s FROM %s WHERE m_uid1=? AND m_uid2=? LIMIT ? OFFSET ?".formatted(COLS, this.name));
        }

        return this.statementQueryByMojangUuid;
    }

    private @NotNull BindingInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
//        "m_uid1, m_uid2, m_name, l_uid1, l_uid2, remark, time";
        final long mUid1 = resultSet.getLong(1);
        final long mUid2 = resultSet.getLong(2);
        final String mName = resultSet.getString(3);
        final long lUid1 = resultSet.getLong(4);
        final long lUid2 = resultSet.getLong(5);
        final String remark = resultSet.getString(6);
        final long time = resultSet.getLong(7);

        return new BindingInfo(new UUID(mUid1, mUid2),
                mName,
                new UUID(lUid1, lUid2),
                remark,
                time);
    }

    private @Nullable BindingInfo parseOne(@NotNull ResultSet resultSet) throws SQLException {

        final BindingInfo info;
        try {
            if (resultSet.next()) info = this.parseRow(resultSet);
            else info = null;

            if (resultSet.next()) throw new SQLException("不应该还有数据！");

        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }

        resultSet.close();

        return info;
    }

    private @NotNull List<BindingInfo> parseAll(@NotNull ResultSet resultSet) throws SQLException {

        final List<BindingInfo> list = new LinkedList<>();

        try {
            while (resultSet.next()) list.add(this.parseRow(resultSet));
        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }

        resultSet.close();

        return list;
    }


    int insert(@NotNull BindingInfo info) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();

//        ("INSERT INTO %s (m_uid1, m_uid2, m_name, l_uid1, l_uid2, remark, time) VALUES (?, ?, ?, ?, ?, ?, ?)".formatted(this.name));
        ps.setLong(1, info.mojangUuid().getMostSignificantBits());
        ps.setLong(2, info.mojangUuid().getLeastSignificantBits());
        ps.setString(3, info.name());

        ps.setLong(4, info.littleSkinUuid().getMostSignificantBits());
        ps.setLong(5, info.littleSkinUuid().getLeastSignificantBits());

        ps.setString(6, info.remark());
        ps.setLong(7, info.time());

        return ps.executeUpdate();
    }

    int delete(@NotNull UUID mojang, @NotNull UUID littleSkin) throws SQLException {
        final PreparedStatement ps = this.getStatementDelete();
        ps.setLong(1, mojang.getMostSignificantBits());
        ps.setLong(2, mojang.getLeastSignificantBits());

        ps.setLong(3, littleSkin.getMostSignificantBits());
        ps.setLong(4, littleSkin.getLeastSignificantBits());

        return ps.executeUpdate();

    }

    @Nullable BindingInfo queryByLittleSkinUuid(@NotNull UUID littleSkinUuid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByLSkinUuid();

        ps.setLong(1, littleSkinUuid.getMostSignificantBits());
        ps.setLong(2, littleSkinUuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();

        return this.parseOne(resultSet);
    }

    @NotNull List<BindingInfo> queryByMojangUuid(@NotNull UUID uuid, int limit, int offset) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByMojangUuid();

        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());

        ps.setInt(3, limit);
        ps.setInt(4, offset);

        final ResultSet resultSet = ps.executeQuery();

        return this.parseAll(resultSet);
    }
}
