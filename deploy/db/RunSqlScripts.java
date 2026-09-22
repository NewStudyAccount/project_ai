import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 简易 SQL 脚本执行器：按分号切分执行；支持 -- 注释。
 * 用法: java -cp <mysql-connector-j.jar> RunSqlScripts.java <jdbcUrlWithoutDb> <user> <password> [db:]file.sql ...
 */
public class RunSqlScripts {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: RunSqlScripts <host> <user> <password> [db:]file.sql ...");
            System.exit(2);
        }
        String host = args[0];
        String user = args[1];
        String password = args[2];
        Class.forName("com.mysql.cj.jdbc.Driver");

        for (int i = 3; i < args.length; i++) {
            String spec = args[i];
            String db = null;
            String file = spec;
            int colon = spec.indexOf(':');
            // Windows path C:\... — treat drive letter as not a db prefix
            if (colon == 1 && Character.isLetter(spec.charAt(0))) {
                file = spec;
                db = null;
            } else if (colon > 1) {
                db = spec.substring(0, colon);
                file = spec.substring(colon + 1);
            }
            Path path = Path.of(file);
            String sql = Files.readString(path, StandardCharsets.UTF_8);
            String url = db == null || db.isBlank()
                    ? "jdbc:mysql://" + host + ":3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowMultiQueries=true&rewriteBatchedStatements=true"
                    : "jdbc:mysql://" + host + ":3306/" + db + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowMultiQueries=true";
            System.out.println("== EXEC " + path + (db == null ? "" : " on " + db));
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement st = conn.createStatement()) {
                for (String stmt : split(sql)) {
                    if (stmt.isBlank()) {
                        continue;
                    }
                    try {
                        boolean hasResult = st.execute(stmt);
                        if (hasResult) {
                            st.getResultSet().close();
                        }
                        System.out.println("   OK (" + st.getUpdateCount() + "): " + preview(stmt));
                    } catch (SQLException ex) {
                        System.err.println("   FAIL: " + preview(stmt));
                        System.err.println("   " + ex.getMessage());
                        throw ex;
                    }
                }
            }
        }
        System.out.println("ALL_DONE");
    }

    static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char n = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    cur.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && n == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick) {
                if (c == '-' && n == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && n == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
                if (c == ';') {
                    String s = cur.toString().trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                    cur.setLength(0);
                    continue;
                }
            }
            if (c == '\'' && !inDouble && !inBacktick) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle && !inBacktick) {
                inDouble = !inDouble;
            } else if (c == '`' && !inSingle && !inDouble) {
                inBacktick = !inBacktick;
            }
            cur.append(c);
        }
        String s = cur.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
        return out;
    }

    static String preview(String stmt) {
        String one = stmt.replaceAll("\\s+", " ").trim();
        return one.length() > 100 ? one.substring(0, 100) + "..." : one;
    }
}
