package cn.paper_card.little_skin_login;

import cn.paper_card.database.api.Util;
import cn.paper_card.little_skin_login.api.BindingCodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

class BindingCodeTable {

    private PreparedStatement statementInsert = null;

    private PreparedStatement statementUpdateByUuid = null;

    private PreparedStatement statementDeleteByCode = null;

    private PreparedStatement statementDeleteTimeBefore = null;

    private PreparedStatement statementQueryByCode = null;

    private PreparedStatement statementQueryCount = null;

    private final @NotNull String name;
    private final @NotNull Connection connection;


    BindingCodeTable(@NotNull Connection connection, @NotNull String name) throws SQLException {
        this.name = name;
        this.connection = connection;
        this.createTable();
    }

    private void createTable() throws SQLException {

        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    code INT NOT NULL UNIQUE,
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    name VARCHAR(64) NOT NULL,
                    time BIGINT NOT NULL,
                    PRIMARY KEY(uid1, uid2)
                )""".formatted(this.name));
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement
                    ("INSERT INTO %s (code, uid1, uid2, name, time) VALUES (?, ?, ?, ? ,?)".formatted(this.name));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementUpdateByUuid() throws SQLException {
        if (this.statementUpdateByUuid == null) {
            this.statementUpdateByUuid = this.connection.prepareStatement
                    ("UPDATE %s SET code=?, name=?, time=? WHERE uid1=? AND uid2=? LIMIT 1".formatted(this.name));
        }
        return this.statementUpdateByUuid;
    }

    private @NotNull PreparedStatement getStatementDeleteByCode() throws SQLException {
        if (this.statementDeleteByCode == null) {
            this.statementDeleteByCode = this.connection.prepareStatement
                    ("DELETE FROM %s WHERE code=? LIMIT 1".formatted(this.name));
        }
        return statementDeleteByCode;
    }

    private @NotNull PreparedStatement getStatementDeleteTimeBefore() throws SQLException {
        if (this.statementDeleteTimeBefore == null) {
            this.statementDeleteTimeBefore = this.connection.prepareStatement
                    ("DELETE FROM %s WHERE time<?".formatted(this.name));
        }
        return this.statementDeleteTimeBefore;
    }

    private @NotNull PreparedStatement getStatementQueryByCode() throws SQLException {
        if (this.statementQueryByCode == null) {
            this.statementQueryByCode = this.connection.prepareStatement
                    ("SELECT code, uid1, uid2, name, time FROM %s WHERE code=? LIMIT 1".formatted(this.name));
        }
        return this.statementQueryByCode;
    }

    private @NotNull PreparedStatement getStatementQueryCount() throws SQLException {
        if (this.statementQueryCount == null) {
            this.statementQueryCount = this.connection.prepareStatement("SELECT count(*) FROM %s".formatted(this.name));
        }
        return this.statementQueryCount;
    }

    private @NotNull BindingCodeInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {

        final int code = resultSet.getInt(1);
        final long uid1 = resultSet.getLong(2);
        final long udi2 = resultSet.getLong(3);
        final String name = resultSet.getString(4);
        final long time = resultSet.getLong(5);

        return new BindingCodeInfo(
                code,
                new UUID(uid1, udi2),
                name,
                time
        );
    }

    private @Nullable BindingCodeInfo parseOne(@NotNull ResultSet resultSet) throws SQLException {

        final BindingCodeInfo info;
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


    int insert(int code, @NotNull UUID uuid, @NotNull String name, long time) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setInt(1, code);
        ps.setLong(2, uuid.getMostSignificantBits());
        ps.setLong(3, uuid.getLeastSignificantBits());
        ps.setString(4, name);
        ps.setLong(5, time);
        return ps.executeUpdate();
    }

    int updateByUuid(@NotNull UUID uuid, int code, @NotNull String name, long time) throws SQLException {
        final PreparedStatement ps = this.getStatementUpdateByUuid();

        ps.setInt(1, code);
        ps.setString(2, name);
        ps.setLong(3, time);

        ps.setLong(4, uuid.getMostSignificantBits());
        ps.setLong(5, uuid.getLeastSignificantBits());

        return ps.executeUpdate();
    }


    @Nullable BindingCodeInfo queryByCode(int code) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByCode();
        ps.setInt(1, code);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }

    int deleteByCode(int code) throws SQLException {
        final PreparedStatement ps = this.getStatementDeleteByCode();
        ps.setInt(1, code);
        return ps.executeUpdate();
    }

    int deleteTimeBefore(long time) throws SQLException {
        final PreparedStatement ps = this.getStatementDeleteTimeBefore();
        ps.setLong(1, time);
        return ps.executeUpdate();
    }

    int queryCount() throws SQLException {
        final PreparedStatement ps = this.getStatementQueryCount();
        final ResultSet resultSet = ps.executeQuery();

        final int c;

        try {
            if (resultSet.next()) c = resultSet.getInt(1);
            else throw new SQLException("应该有数据！");

            if (resultSet.next()) throw new SQLException("不应该还有数据！");
        } catch (SQLException e) {

            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }

        resultSet.close();

        return c;
    }


}
