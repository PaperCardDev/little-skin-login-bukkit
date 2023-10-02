package cn.paper_card.little_skin_login;

import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

class BindCodeTable {

    private final PreparedStatement statementInsert;

    private final PreparedStatement statementUpdateByUuid;

    private final PreparedStatement statementQueryByCode;

    private final PreparedStatement statementDeleteByCode;

    private final PreparedStatement statementQueryCount;

    private final PreparedStatement statementDeleteTimeBefore;

    private final @NotNull String name;


    public BindCodeTable(@NotNull Connection connection, @NotNull String name) throws SQLException {
        this.name = name;
        this.createTable(connection);

        try {
            this.statementInsert = connection.prepareStatement
                    ("INSERT INTO %s (code, uid1, uid2, name, time) VALUES (?, ?, ?, ? ,?)".formatted(this.name));

            this.statementUpdateByUuid = connection.prepareStatement
                    ("UPDATE %s SET code=?, name=?, time=? WHERE uid1=? AND uid2=?".formatted(this.name));

            this.statementQueryByCode = connection.prepareStatement
                    ("SELECT code, uid1, uid2, name, time FROM %s WHERE code=?".formatted(this.name));

            this.statementDeleteByCode = connection.prepareStatement
                    ("DELETE FROM %s WHERE code=?".formatted(this.name));

            this.statementQueryCount = connection.prepareStatement
                    ("SELECT count(*) FROM %s".formatted(this.name));

            this.statementDeleteTimeBefore = connection.prepareStatement
                    ("DELETE FROM %s WHERE time<?".formatted(this.name));

        } catch (SQLException e) {
            try {
                this.close();
            } catch (SQLException ignored) {
            }

            throw e;
        }
    }

    private void createTable(@NotNull Connection connection) throws SQLException {

        DatabaseConnection.createTable(connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    code INTEGER NOT NULL PRIMARY KEY,
                    uid1 INTEGER NOT NULL,
                    uid2 INTEGER NOT NULL,
                    name VARCHAR(48) NOT NULL,
                    time INTEGER NOT NULL
                )""".formatted(this.name));
    }

    public void close() throws SQLException {
        DatabaseConnection.closeAllStatements(this.getClass(), this);
    }

    public int insert(int code, @NotNull UUID uuid, @NotNull String name, long time) throws SQLException {
        final PreparedStatement ps = this.statementInsert;
        ps.setInt(1, code);
        ps.setLong(2, uuid.getMostSignificantBits());
        ps.setLong(3, uuid.getLeastSignificantBits());
        ps.setString(4, name);
        ps.setLong(5, time);
        return ps.executeUpdate();
    }

    public int updateByUuid(@NotNull UUID uuid, int code, @NotNull String name, long time) throws SQLException {
        final PreparedStatement ps = this.statementUpdateByUuid;
        ps.setInt(1, code);
        ps.setString(2, name);
        ps.setLong(3, time);
        ps.setLong(4, uuid.getMostSignificantBits());
        ps.setLong(5, uuid.getLeastSignificantBits());
        return ps.executeUpdate();
    }

    private @NotNull List<LittleSkinLoginApi.BindCodeInfo> parse(@NotNull ResultSet resultSet) throws SQLException {
        final LinkedList<LittleSkinLoginApi.BindCodeInfo> list = new LinkedList<>();

//        ("SELECT code, uid1, uid2, name, time FROM %s WHERE code=?".formatted(this.name));
        try {
            while (resultSet.next()) {
                final int code = resultSet.getInt(1);
                final long uid1 = resultSet.getLong(2);
                final long udi2 = resultSet.getLong(3);
                final String name = resultSet.getString(4);
                final long time = resultSet.getLong(5);

                list.add(new LittleSkinLoginApi.BindCodeInfo(
                        code,
                        new UUID(uid1, udi2),
                        name,
                        time
                ));
            }
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

    public @NotNull List<LittleSkinLoginApi.BindCodeInfo> queryByCode(int code) throws SQLException {
        final PreparedStatement ps = this.statementQueryByCode;
        ps.setInt(1, code);
        final ResultSet resultSet = ps.executeQuery();
        return this.parse(resultSet);
    }

    public int deleteByCode(int code) throws SQLException {
        final PreparedStatement ps = this.statementDeleteByCode;
        ps.setInt(1, code);
        return ps.executeUpdate();
    }

    public int deleteTimeBefore(long time) throws SQLException {
        final PreparedStatement ps = this.statementDeleteTimeBefore;
        ps.setLong(1, time);
        return ps.executeUpdate();
    }

    public int queryCount() throws SQLException {
        final ResultSet resultSet = this.statementQueryCount.executeQuery();

        final int c;

        try {
            if (resultSet.next()) {
                c = resultSet.getInt(1);
            } else throw new SQLException("未知SQL异常！");
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
