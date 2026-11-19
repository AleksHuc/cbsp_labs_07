package cbsp.lab07;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Headless time plots for the prepared experiments. */
final class Plots {
    private static final List<Color> COLORS = List.of(new Color(0x0072B2), new Color(0xD55E00),
            new Color(0x009E73), new Color(0x963F9C));

    private Plots() { }

    record Point(double x, double y) { }

    record Series(String label, List<Point> points, boolean connect) { }

    static void write(Path path, String title, String xLabel, String yLabel, double xMax, double yMax,
                      int yTicks, List<Series> series) throws IOException {
        int left = 116, right = 856, top = 88, bottom = 388;
        var image = new BufferedImage(900, 450, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.DARK_GRAY);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
            graphics.drawString(title, 24, 30);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            graphics.drawString(yLabel, 24, top - 10);
            graphics.drawString(xLabel, right - graphics.getFontMetrics().stringWidth(xLabel), 432);

            for (int i = 0; i <= 5; i++) {
                int x = left + (right - left) * i / 5;
                graphics.setColor(new Color(0xE2E7ED));
                graphics.drawLine(x, top, x, bottom);
                graphics.setColor(Color.DARK_GRAY);
                graphics.drawString(label(xMax * i / 5), x - 10, bottom + 20);
            }
            for (int i = 0; i <= yTicks; i++) {
                int y = bottom - (bottom - top) * i / yTicks;
                graphics.setColor(new Color(0xE2E7ED));
                graphics.drawLine(left, y, right, y);
                graphics.setColor(Color.DARK_GRAY);
                graphics.drawString(label(yMax * i / yTicks), left - 35, y + 4);
            }

            graphics.setStroke(new BasicStroke(2.5f));
            int legend = 24;
            for (int i = 0; i < series.size(); i++) {
                Series item = series.get(i);
                graphics.setColor(COLORS.get(i));
                graphics.fillOval(legend, 45, 8, 8);
                graphics.drawString(item.label(), legend + 14, 54);
                legend += graphics.getFontMetrics().stringWidth(item.label()) + 38;

                Point previous = null;
                for (Point point : item.points()) {
                    int x = left + (int) Math.round(point.x() / xMax * (right - left));
                    int y = bottom - (int) Math.round(point.y() / yMax * (bottom - top));
                    if (!item.connect()) {
                        graphics.fillOval(x - 4, y - 4, 8, 8);
                    } else if (previous != null) {
                        int lastX = left + (int) Math.round(previous.x() / xMax * (right - left));
                        int lastY = bottom - (int) Math.round(previous.y() / yMax * (bottom - top));
                        graphics.drawLine(lastX, lastY, x, y);
                    }
                    previous = point;
                }
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static String label(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
