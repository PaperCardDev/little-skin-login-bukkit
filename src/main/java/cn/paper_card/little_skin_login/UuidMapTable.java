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

class UuidMapTable {

    private final PreparedStatement statementInsert;

    private final PreparedStatement statementUpdateByMojangUuid;

    private final PreparedStatement statementQueryByMojangUuid;
    private final PreparedStatement statementQueryByLSkinUuid;

    private final @NotNull String name;

    UuidMapTable(@NotNull Connection connection, @NotNull String name) throws SQLException {
        this.name = name;

        this.create(connection);

        try {

            this.statementInsert = connection.prepareStatement
                    ("INSERT INTO %s (m_uid1, m_uid2, l_uid1, l_uid2) VALUES (?, ?, ?, ?)".formatted(this.name));

            this.statementUpdateByMojangUuid = connection.prepareStatement
                    ("UPDATE %s SET l_uid1=?, l_uid2=? WHERE m_uid1=? AND m_uid2=?".formatted(this.name));

            this.statementQueryByMojangUuid = connection.prepareStatement
                    ("SELECT l_uid1, l_uid2 FROM %s WHERE m_uid1=? AND m_uid2=?".formatted(this.name));

            this.statementQueryByLSkinUuid = connection.prepareStatement
                    ("SELECT m_uid1, m_uid2 FROM %s WHERE l_uid1=? AND l_uid2=?".formatted(this.name));

        } catch (SQLException e) {
            try {
                this.close();
            } catch (SQLException ignored) {
            }

            throw e;
        }
    }

    private void create(@NotNull Connection connection) throws SQLException {
        DatabaseConnection.createTable(connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    m_uid1 INTEGER NOT NULL,
                    m_uid2 INTEGER NOT NULL,
                    l_uid1 INTEGER NOT NULL,
                    L_uid2 INTEGER NOT NULL
                )""".formatted(this.name));
    }

    void close() throws SQLException {
        DatabaseConnection.closeAllStatements(this.getClass(), this);
    }

    private @NotNull List<UUID> parseUuids(@NotNull ResultSet resultSet) throws SQLException {

        final LinkedList<UUID> list = new LinkedList<>();
        try {
            while (resultSet.next()) {
                final long uid1 = resultSet.getLong(1);
                final long uid2 = resultSet.getLong(2);
                list.add(new UUID(uid1, uid2));
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


    int insert(@NotNull UUID mojangUuid, @NotNull UUID littleSkinUuid) throws SQLException {
        final PreparedStatement ps = this.statementInsert;
        ps.setLong(1, mojangUuid.getMostSignificantBits());
        ps.setLong(2, mojangUuid.getLeastSignificantBits());
        ps.setLong(3, littleSkinUuid.getMostSignificantBits());
        ps.setLong(4, littleSkinUuid.getLeastSignificantBits());
        return this.statementInsert.executeUpdate();
    }

    int updateMyMojangUuid(@NotNull UUID mojangUuid, @NotNull UUID littleSkinUuid) throws SQLException {
        final PreparedStatement ps = this.statementUpdateByMojangUuid;
        ps.setLong(1, littleSkinUuid.getMostSignificantBits());
        ps.setLong(2, littleSkinUuid.getLeastSignificantBits());
        ps.setLong(3, mojangUuid.getMostSignificantBits());
        ps.setLong(4, mojangUuid.getLeastSignificantBits());
        return ps.executeUpdate();
    }


    @NotNull List<UUID> queryByMojangUuid(@NotNull UUID mojangUuid) throws SQLException {
        final PreparedStatement ps = this.statementQueryByMojangUuid;

        ps.setLong(1, mojangUuid.getMostSignificantBits());
        ps.setLong(2, mojangUuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();

        return this.parseUuids(resultSet);
    }

    @NotNull List<UUID> queryByLittleSkinUuid(@NotNull UUID littleSkinUuid) throws SQLException {
        final PreparedStatement ps = this.statementQueryByLSkinUuid;

        ps.setLong(1, littleSkinUuid.getMostSignificantBits());
        ps.setLong(2, littleSkinUuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();

        return this.parseUuids(resultSet);
    }
}
