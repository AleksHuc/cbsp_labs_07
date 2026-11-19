package cbsp.lab07;

import java.util.StringJoiner;

final class Csv {
    private Csv() { }

    static String row(Object... columns) {
        StringJoiner row = new StringJoiner(",", "", "\n");
        for (Object column : columns) {
            String text = column == null ? "" : column.toString();
            if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                    || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                text = '"' + text.replace("\"", "\"\"") + '"';
            }
            row.add(text);
        }
        return row.toString();
    }
}
