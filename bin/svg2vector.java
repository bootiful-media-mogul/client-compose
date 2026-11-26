import com.android.ide.common.vectordrawable.Svg2Vector;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * turns an svg into an android vector drawable, with the converter android studio's vector asset
 * wizard uses: java -cp <sdk-common, common, guava> bin/svg2vector.java in.svg out.xml. compose
 * multiplatform draws vector drawables on every platform, but svgs on all of them except android,
 * where painterResource throws on one. it fails on anything the drawable can't draw (masks,
 * filters, text), rather than writing a picture that's quietly wrong
 */
class svg2vector {

	public static void main(String[] args) throws Exception {
		var in = Path.of(args[0]);
		var out = Path.of(args[1]);
		String errors;
		try (OutputStream os = Files.newOutputStream(out)) {
			errors = Svg2Vector.parseSvgToXml(in, os);
		}
		if (errors != null && !errors.isBlank()) {
			Files.delete(out);
			System.err.println(in + ": " + errors);
			System.exit(1);
		}
	}

}
