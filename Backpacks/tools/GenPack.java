import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates the 17 tier textures, 3D models and item definitions for the client mod.
 *
 * <p>Each texture is a 32x32 atlas of eight 8x8 tiles (2 columns layout 4 wide x 2 tall):
 * body, flap, pocket, strap / buckle, side, accent, handle. The 3D model maps each
 * cube face onto one of those tiles, so a single texture per tier is enough.
 *
 * <p>Run: {@code java tools/GenPack.java mod/src/main/resources}
 */
public final class GenPack {
    private static final String[] IDS = {
            "linen_pouch", "leather_satchel", "copper_pack", "iron_pack",
            "gold_pack", "redstone_pack", "lapis_pack", "amethyst_pack",
            "emerald_pack", "quartz_pack", "obsidian_pack", "diamond_pack",
            "blaze_pack", "ender_pack", "netherite_pack", "dragon_pack",
            "ultimate_pack"
    };
    private static final int[] BODY = {
            0xB08A5A, 0x8B5A2B, 0xC77B4E, 0xB8BCC4,
            0xE0B23C, 0xB83232, 0x2D5BD1, 0x8E5BC9,
            0x2AA65C, 0xD9D2C4, 0x3B2A55, 0x35C7C0,
            0xD9731C, 0x1F7A56, 0x4A3F3A, 0x7B2FBE,
            0x1E1436
    };
    private static final int[] ACCENT = {
            0xE8D3A9, 0xD9A066, 0xF0B27A, 0xE8ECF2,
            0xFFE38A, 0xFF6B6B, 0x7FA7FF, 0xD7B3FF,
            0x7BEEA9, 0xFFF8EC, 0x8A6BC4, 0x9BFFF8,
            0xFFC24A, 0x5CF2B0, 0xA89A90, 0xE58BFF,
            0xF5C542
    };

    private static final int BRASS = 0xD8A93A;

    public static void main(String[] args) throws Exception {
        Path resources = Paths.get(args.length > 0 ? args[0] : "mod/src/main/resources");
        String namespace = args.length > 1 ? args[1] : "backpacks";
        Path textures = resources.resolve("assets/" + namespace + "/textures/item");
        Path models = resources.resolve("assets/" + namespace + "/models/item");
        Path items = resources.resolve("assets/" + namespace + "/items");
        Files.createDirectories(textures);
        Files.createDirectories(models);
        Files.createDirectories(items);

        for (int i = 0; i < IDS.length; i++) {
            int number = i + 1;
            BufferedImage image = atlas(BODY[i], ACCENT[i], number);
            ImageIO.write(image, "png", textures.resolve("backpack_" + number + ".png").toFile());

            Files.writeString(models.resolve("backpack_" + number + ".json"),
                    model(namespace, number), StandardCharsets.UTF_8);

            String item = "{\n"
                    + "  \"model\": {\n"
                    + "    \"type\": \"minecraft:model\",\n"
                    + "    \"model\": \"" + namespace + ":item/backpack_" + number + "\"\n"
                    + "  }\n"
                    + "}\n";
            Files.writeString(items.resolve("backpack_" + number + ".json"), item, StandardCharsets.UTF_8);
        }
        System.out.println("Generated " + IDS.length + " backpack atlases, 3D models and item definitions");
    }

    // ------------------------------------------------------------------ texture

    private static BufferedImage atlas(int body, int accent, int number) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        int buckle = number >= 11 ? lighten(accent, 0.10) : BRASS;

        tileBody(image, 0, 0, body);
        tileFlap(image, 1, 0, accent);
        tilePocket(image, 2, 0, accent);
        tileStrap(image, 3, 0, body);
        tileBuckle(image, 0, 1, buckle);
        tileSide(image, 1, 1, body);
        tileAccent(image, 2, 1, accent);
        tileHandle(image, 3, 1, body);

        if (number == 17) {
            star(image, 0, 0, accent);
            star(image, 2, 1, accent);
        }
        return image;
    }

    /** Golden eight-pointed star stamped onto the ultimate backpack. */
    private static void star(BufferedImage img, int col, int row, int color) {
        int x = col * 8;
        int y = row * 8;
        int bright = lighten(color, 0.70);
        int core = lighten(color, 0.25);
        int[] points = {
                3, 1, 4, 1,
                2, 2, 3, 2, 4, 2, 5, 2,
                1, 3, 2, 3, 5, 3, 6, 3,
                1, 4, 2, 4, 5, 4, 6, 4,
                2, 5, 3, 5, 4, 5, 5, 5,
                3, 6, 4, 6
        };
        for (int i = 0; i < points.length; i += 2) {
            px(img, x + points[i], y + points[i + 1], bright);
        }
        for (int py = 3; py <= 4; py++) {
            for (int px = 3; px <= 4; px++) {
                px(img, x + px, y + py, core);
            }
        }
    }

    /** x0,y0 = top-left pixel of the 8x8 cell. */
    private static void tileBody(BufferedImage img, int col, int row, int body) {
        int x = col * 8;
        int y = row * 8;
        int base = body;
        int top = lighten(body, 0.22);
        int bottom = shade(body, 0.22);
        int edge = shade(body, 0.32);
        int stitch = lighten(body, 0.55);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, top);
        hline(img, x, x + 7, y + 7, bottom);
        vline(img, x, y, y + 7, edge);
        vline(img, x + 7, y, y + 7, edge);
        px(img, x + 2, y + 5, stitch);
        px(img, x + 5, y + 5, stitch);
    }

    private static void tileFlap(BufferedImage img, int col, int row, int accent) {
        int x = col * 8;
        int y = row * 8;
        fill(img, x, y, accent);
        hline(img, x, x + 7, y, lighten(accent, 0.30));
        hline(img, x, x + 7, y + 7, shade(accent, 0.28));
        vline(img, x, y, y + 7, shade(accent, 0.18));
        vline(img, x + 7, y, y + 7, shade(accent, 0.18));
        int stitch = lighten(accent, 0.55);
        for (int i = 0; i < 8; i += 2) {
            px(img, x + i, y + 6, stitch);
        }
    }

    private static void tilePocket(BufferedImage img, int col, int row, int accent) {
        int x = col * 8;
        int y = row * 8;
        int base = shade(accent, 0.10);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, lighten(accent, 0.20));
        hline(img, x, x + 7, y + 7, shade(accent, 0.35));
        vline(img, x, y, y + 7, shade(accent, 0.22));
        vline(img, x + 7, y, y + 7, shade(accent, 0.22));
    }

    private static void tileStrap(BufferedImage img, int col, int row, int body) {
        int x = col * 8;
        int y = row * 8;
        int base = shade(body, 0.55);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, shade(body, 0.45));
        hline(img, x, x + 7, y + 7, shade(body, 0.65));
        vline(img, x, y, y + 7, shade(body, 0.42));
        vline(img, x + 7, y, y + 7, shade(body, 0.42));
    }

    private static void tileBuckle(BufferedImage img, int col, int row, int buckle) {
        int x = col * 8;
        int y = row * 8;
        fill(img, x, y, buckle);
        hline(img, x, x + 7, y, lighten(buckle, 0.35));
        hline(img, x, x + 7, y + 7, shade(buckle, 0.40));
        vline(img, x, y, y + 7, shade(buckle, 0.35));
        vline(img, x + 7, y, y + 7, shade(buckle, 0.35));
        int hot = lighten(buckle, 0.70);
        px(img, x + 3, y + 3, hot);
        px(img, x + 4, y + 3, hot);
        px(img, x + 3, y + 4, hot);
        px(img, x + 4, y + 4, shade(buckle, 0.15));
    }

    private static void tileSide(BufferedImage img, int col, int row, int body) {
        int x = col * 8;
        int y = row * 8;
        int base = shade(body, 0.24);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, shade(body, 0.10));
        hline(img, x, x + 7, y + 7, shade(body, 0.38));
        vline(img, x, y, y + 7, shade(body, 0.34));
        vline(img, x + 7, y, y + 7, shade(body, 0.34));
    }

    private static void tileAccent(BufferedImage img, int col, int row, int accent) {
        int x = col * 8;
        int y = row * 8;
        int base = lighten(accent, 0.18);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, lighten(accent, 0.42));
        hline(img, x, x + 7, y + 7, shade(accent, 0.05));
        vline(img, x, y, y + 7, shade(accent, 0.08));
        vline(img, x + 7, y, y + 7, shade(accent, 0.08));
    }

    private static void tileHandle(BufferedImage img, int col, int row, int body) {
        int x = col * 8;
        int y = row * 8;
        int base = shade(body, 0.45);
        fill(img, x, y, base);
        hline(img, x, x + 7, y, shade(body, 0.28));
        hline(img, x, x + 7, y + 7, shade(body, 0.62));
        vline(img, x + 1, y, y + 7, shade(body, 0.30));
        vline(img, x + 6, y, y + 7, shade(body, 0.60));
    }

    private static void fill(BufferedImage img, int x, int y, int rgb) {
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                img.setRGB(x + i, y + j, argb(rgb));
            }
        }
    }

    private static void hline(BufferedImage img, int x0, int x1, int y, int rgb) {
        for (int x = x0; x <= x1; x++) {
            img.setRGB(x, y, argb(rgb));
        }
    }

    private static void vline(BufferedImage img, int x, int y0, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) {
            img.setRGB(x, y, argb(rgb));
        }
    }

    private static void px(BufferedImage img, int x, int y, int rgb) {
        img.setRGB(x, y, argb(rgb));
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int shade(int rgb, double amount) {
        return scale(rgb, 1.0 - amount);
    }

    private static int lighten(int rgb, double amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) Math.round(r + (255 - r) * amount);
        g = (int) Math.round(g + (255 - g) * amount);
        b = (int) Math.round(b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    // -------------------------------------------------------------------- model

    private static String model(String namespace, int number) {
        String tex = namespace + ":item/backpack_" + number;
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"parent\": \"minecraft:block/block\",\n");
        sb.append("  \"textures\": {\n");
        sb.append("    \"particle\": \"").append(tex).append("\",\n");
        sb.append("    \"body\": \"").append(tex).append("\",\n");
        sb.append("    \"flap\": \"").append(tex).append("\",\n");
        sb.append("    \"pocket\": \"").append(tex).append("\",\n");
        sb.append("    \"strap\": \"").append(tex).append("\",\n");
        sb.append("    \"buckle\": \"").append(tex).append("\",\n");
        sb.append("    \"side\": \"").append(tex).append("\",\n");
        sb.append("    \"accent\": \"").append(tex).append("\",\n");
        sb.append("    \"handle\": \"").append(tex).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"elements\": [\n");

        // Body: 10 wide, 10 tall, 4 deep, centered on x/z.
        element(sb, 3, 3, 6, 13, 13, 10, "body", cell(0, 0), cell(1, 1), cell(1, 1), true);

        // Straps behind the body.
        element(sb, 4.3, 4, 10.05, 6.1, 12.5, 10.45, "strap", cell(3, 0), cell(3, 0), cell(3, 0), true);
        element(sb, 9.9, 4, 10.05, 11.7, 12.5, 10.45, "strap", cell(3, 0), cell(3, 0), cell(3, 0), true);

        // Lid over the top.
        element(sb, 2.6, 11.2, 5.4, 13.4, 13.2, 10.6, "flap", cell(1, 0), cell(2, 1), cell(1, 0), true);

        // Front flap hanging over the opening.
        element(sb, 2.6, 8.4, 5.35, 13.4, 11.4, 6.2, "flap", cell(1, 0), cell(2, 1), cell(1, 0), true);

        // Front pocket.
        element(sb, 4.8, 3.6, 4.7, 11.2, 8.0, 5.6, "pocket", cell(2, 0), cell(2, 1), cell(2, 0), true);

        // Buckle on the flap.
        element(sb, 7, 9.3, 5.05, 9, 10.3, 5.34, "buckle", cell(0, 1), cell(0, 1), cell(0, 1), true);

        // Carry handle on top.
        element(sb, 6, 13.2, 7.2, 10, 14.0, 8.8, "handle", cell(3, 1), cell(3, 1), cell(3, 1), true);

        sb.setLength(sb.length() - 2);
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private record Cell(int col, int row) {
    }

    private static Cell cell(int col, int row) {
        return new Cell(col, row);
    }

    /**
     * Appends one cube. All six sides are drawn explicitly; {@code side} is used for the
     * vertical faces, {@code up}/{@code down} for the horizontal ones.
     */
    private static void element(StringBuilder sb, double x1, double y1, double z1,
                                double x2, double y2, double z2, String tex,
                                Cell side, Cell up, Cell down, boolean last) {
        sb.append("    {\n");
        sb.append("      \"from\": [").append(fmt(x1)).append(", ").append(fmt(y1)).append(", ").append(fmt(z1)).append("],\n");
        sb.append("      \"to\": [").append(fmt(x2)).append(", ").append(fmt(y2)).append(", ").append(fmt(z2)).append("],\n");
        sb.append("      \"faces\": {\n");
        sb.append("        \"north\": ").append(face(side, tex)).append(",\n");
        sb.append("        \"east\": ").append(face(side, tex)).append(",\n");
        sb.append("        \"south\": ").append(face(side, tex)).append(",\n");
        sb.append("        \"west\": ").append(face(side, tex)).append(",\n");
        sb.append("        \"up\": ").append(face(up, tex)).append(",\n");
        sb.append("        \"down\": ").append(face(down, tex)).append("\n");
        sb.append("      }\n");
        sb.append(last ? "    },\n" : "    }\n");
    }

    private static String face(Cell cell, String tex) {
        double u0 = cell.col() * 4.0;
        double v0 = cell.row() * 4.0;
        return "{\"uv\": [" + fmt(u0) + ", " + fmt(v0) + ", " + fmt(u0 + 4) + ", " + fmt(v0 + 4)
                + "], \"texture\": \"#" + tex + "\"}";
    }

    private static String fmt(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String text = String.valueOf(value);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }
}
