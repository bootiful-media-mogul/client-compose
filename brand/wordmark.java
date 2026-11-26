//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates the outlined Mogul logo using only the JDK (Java 17 or newer).
 * Run from the project directory: java -Djava.awt.headless=true brand/wordmark.java
 * Options: --mark mark.svg --product CLIP --out logo.svg
 * The bundled fonts are resolved relative to this source file, or an ancestor of
 * the working directory when running the compiled class.
 */
record Word(String path, Rectangle2D bounds, double end) {
}

void main(String[] args) throws Exception {
    var root = root();
    var markFile = root.resolve("brand/mogul-logo.svg");
    var output = root.resolve("brand/mogul-studio-logo.svg");
    var product = "STUDIO";
    for (var i = 0; i < args.length; i++) {
        var option = args[i];
        if (option.equals("--help") || option.equals("-h")) {
            System.out.println("Usage: java brand/wordmark.java [--mark mark.svg] [--product STUDIO] [--out logo.svg]");
            return;
        }
        if (!option.equals("--mark") && !option.equals("--product") && !option.equals("--out")) {
            throw new IllegalArgumentException("Unknown option: " + option);
        }
        if (++i == args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        switch (option) {
            case "--mark" -> markFile = Path.of(args[i]);
            case "--product" -> product = args[i];
            case "--out" -> output = Path.of(args[i]);
            default -> throw new AssertionError(option);
        }
    }
    var fonts = root.resolve("shared/src/commonMain/composeResources/font");
    var name = word(fonts.resolve("spacegrotesk_bold.ttf"), "mogul", 100, -5, 125 + 100.0 / 3);
    var label = word(fonts.resolve("ibmplexmono_medium.ttf"), product, 37.5, 9, name.end() + 25);
    var markTop = name.bounds().getCenterY() - 62.5;
    var mark = Files.readString(markFile, StandardCharsets.UTF_8).replaceAll("(?s)<!--.*?-->", "");
    var match = Pattern.compile("<svg\\b[^>]*>(.*)</svg>", Pattern.DOTALL).matcher(mark);
    if (!match.find()) {
        throw new IllegalArgumentException("Mark must contain an SVG element: " + markFile);
    }
    mark = match.group(1).lines().map(String::strip).filter(line -> !line.isEmpty())
            .map(line -> "      " + line).collect(java.util.stream.Collectors.joining("\n"));
    var top = Math.min(markTop, Math.min(name.bounds().getMinY(), label.bounds().getMinY()));
    var bottom = Math.max(markTop + 125, Math.max(name.bounds().getMaxY(), label.bounds().getMaxY()));
    var width = number(label.bounds().getMaxX());
    var height = number(bottom - top);
    var svg = """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="%s" height="%s" viewBox="0 0 %s %s">
              <!-- the whole mogul %s logo, made by mogul studio's brand/wordmark.java from
                   %s and the studio's fonts. the words are light, for the dark bars it sits on -->
              <g transform="translate(0 %s)">
                <g transform="translate(0 %s) scale(1.25)">
            %s
                </g>
                <path id="name" d="%s" fill="#f2f6fc"/>
                <path id="product" d="%s" fill="#609ea0"/>
              </g>
            </svg>
            """.formatted(width, height, width, height, comment(product.toLowerCase(Locale.ROOT)),
            comment(markFile.getFileName().toString()), number(-top), number(markTop), mark,
            name.path(), label.path());
    Files.writeString(output, svg, StandardCharsets.UTF_8);
    System.out.printf("%s is %s x %s: its aspect ratio is %sf / %sf%n", output.getFileName(), width, height, width, height);
}

Word word(Path file, String text, double size, double tracking, double x) throws Exception {
    var fontContext = new FontRenderContext(new AffineTransform(), true, true);
    var font = Font.createFont(Font.TRUETYPE_FONT, file.toFile()).deriveFont((float) size);
    var path = new StringBuilder();
    var bounds = (Rectangle2D) null;
    var characters = text.codePoints().toArray();
    for (var i = 0; i < characters.length; i++) {
        var character = characters[i];
        if (!font.canDisplay(character)) {
            throw new IllegalArgumentException("Font " + file + " cannot display U+" + Integer.toHexString(character));
        }
        var glyph = font.createGlyphVector(fontContext, new String(Character.toChars(character)));
        var outline = glyph.getOutline((float) x, 0);
        var ink = outline.getBounds2D();
        if (!ink.isEmpty()) {
            bounds = bounds == null ? ink : bounds.createUnion(ink);
        }
        var coordinates = new double[6];
        for (var iterator = outline.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            var type = iterator.currentSegment(coordinates);
            var command = switch (type) {
                case PathIterator.SEG_MOVETO -> 'M';
                case PathIterator.SEG_LINETO -> 'L';
                case PathIterator.SEG_QUADTO -> 'Q';
                case PathIterator.SEG_CUBICTO -> 'C';
                case PathIterator.SEG_CLOSE -> 'Z';
                default -> throw new AssertionError(type);
            };
            path.append(command);
            var count = switch (type) {
                case PathIterator.SEG_CLOSE -> 0;
                case PathIterator.SEG_QUADTO -> 4;
                case PathIterator.SEG_CUBICTO -> 6;
                default -> 2;
            };
            for (var j = 0; j < count; j++) {
                if (j > 0) path.append(' ');
                path.append(number(coordinates[j]));
            }
        }
        x += glyph.getGlyphPosition(glyph.getNumGlyphs()).getX() + (i < characters.length - 1 ? tracking : 0);
    }
    if (bounds == null)
        throw new IllegalArgumentException("Text must contain visible glyphs: " + text);
    return new Word(path.toString(), bounds, x);
}

String number(double value) {
    var result = String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    return result.equals("-0") ? "0" : result;
}

String comment(String value) {
    return value.replace("--", "- -").replace("<", "&lt;").replace(">", "&gt;");
}

Path root() {
    var source = System.getProperty("jdk.launcher.sourcefile");
    var start = source == null ? Path.of("").toAbsolutePath() : Path.of(source).toAbsolutePath().getParent();
    for (var directory = start; directory != null; directory = directory.getParent()) {
        if (Files.isDirectory(directory.resolve("shared/src/commonMain/composeResources/font")))
            return directory;
    }
    throw new IllegalStateException("Cannot find bundled fonts; run from the project directory.");
}
