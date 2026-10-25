package org.biopulse.jdbc;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.stereotype.Component;

/** Transaction-aware JDBC primitive. All callers bind values; SQL structure is constant. */
@Component
public class Sql {
  private final DataSource source;

  public Sql(DataSource source) {
    this.source = source;
  }

  @FunctionalInterface
  public interface Mapper<T> {
    T map(ResultSet rs) throws SQLException;
  }

  public <T> List<T> query(String sql, Mapper<T> mapper, Object... args) {
    Connection c = DataSourceUtils.getConnection(source);
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      bind(ps, args);
      try (ResultSet rs = ps.executeQuery()) {
        List<T> rows = new ArrayList<>();
        while (rs.next()) rows.add(mapper.map(rs));
        return rows;
      }
    } catch (SQLException e) {
      throw translate(sql, e);
    } finally {
      DataSourceUtils.releaseConnection(c, source);
    }
  }

  public int update(String sql, Object... args) {
    Connection c = DataSourceUtils.getConnection(source);
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      bind(ps, args);
      return ps.executeUpdate();
    } catch (SQLException e) {
      throw translate(sql, e);
    } finally {
      DataSourceUtils.releaseConnection(c, source);
    }
  }

  public long insert(String sql, Object... args) {
    Connection c = DataSourceUtils.getConnection(source);
    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(ps, args);
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        if (!rs.next()) throw new SQLException("No generated key");
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw translate(sql, e);
    } finally {
      DataSourceUtils.releaseConnection(c, source);
    }
  }

  public List<Long> batch(String sql, List<Object[]> args) {
    Connection c = DataSourceUtils.getConnection(source);
    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      for (Object[] row : args) {
        bind(ps, row);
        ps.addBatch();
      }
      ps.executeBatch();
      List<Long> keys = new ArrayList<>();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        while (rs.next()) keys.add(rs.getLong(1));
      }
      if (keys.size() != args.size()) throw new SQLException("Batch generated key count mismatch");
      return List.copyOf(keys);
    } catch (SQLException e) {
      throw translate(sql, e);
    } finally {
      DataSourceUtils.releaseConnection(c, source);
    }
  }

  private RuntimeException translate(String sql, SQLException e) {
    RuntimeException translated = new SQLStateSQLExceptionTranslator().translate("JDBC", sql, e);
    return translated != null
        ? translated
        : new org.springframework.jdbc.UncategorizedSQLException("JDBC", sql, e);
  }

  private void bind(PreparedStatement ps, Object[] args) throws SQLException {
    for (int i = 0; i < args.length; i++) {
      Object v = args[i];
      if (v instanceof Instant t) ps.setTimestamp(i + 1, Timestamp.from(t));
      else if (v instanceof Enum<?> e) ps.setString(i + 1, e.name());
      else ps.setObject(i + 1, v);
    }
  }

  public static Instant instant(ResultSet r, String name) throws SQLException {
    Timestamp t = r.getTimestamp(name);
    return t == null ? null : t.toInstant();
  }

  public static Double number(ResultSet r, String name) throws SQLException {
    double v = r.getDouble(name);
    return r.wasNull() ? null : v;
  }

  public static Long nullableLong(ResultSet r, String name) throws SQLException {
    long v = r.getLong(name);
    return r.wasNull() ? null : v;
  }
}
