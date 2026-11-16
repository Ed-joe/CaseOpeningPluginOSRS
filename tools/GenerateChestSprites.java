import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates placeholder pixel-art opening sprite sheets for the non-clue reward chests.
 *
 * Each sheet matches the clue casket sheets: six 48x48 frames in a row, closed chest first,
 * lid swinging open and tipping back by the last frame, with the visible bottom at y=42.
 * The chest is a tiny software-rendered pair of boxes (body + lid) so every source gets a
 * consistent silhouette with its own materials, bands and emblem. Hand-drawn art can replace
 * any sheet later without code changes.
 *
 * Run from the repository root:  java tools/GenerateChestSprites.java
 */
public class GenerateChestSprites
{
	private static final int FRAME = 48;
	private static final int FRAMES = 6;
	private static final double[] LID_ANGLES = {0, 9, 38, 82, 112, 118};
	private static final double YAW = Math.toRadians(28);
	private static final double PITCH = Math.toRadians(30);
	private static final int OUTLINE = 0xFF000001;

	// Chest dimensions in pixels (before projection).
	private static final double WIDTH = 26, DEPTH = 13, BODY_HEIGHT = 11, LID_HEIGHT = 6;

	public static void main(String[] args) throws Exception
	{
		File out = new File(args.length > 0 ? args[0] : "src/main/resources");
		for (Style style : styles())
		{
			BufferedImage sheet = new BufferedImage(FRAME * FRAMES, FRAME, BufferedImage.TYPE_INT_ARGB);
			for (int frame = 0; frame < FRAMES; frame++)
			{
				BufferedImage image = renderFrame(style, LID_ANGLES[frame], frame);
				sheet.getGraphics().drawImage(image, frame * FRAME, 0, null);
			}
			File file = new File(out, "reward-chest-" + style.key + "-sprite-sheet.png");
			ImageIO.write(sheet, "png", file);
			System.out.println("Wrote " + file);
		}
	}

	// ---------------------------------------------------------------- styles

	static final class Style
	{
		String key;
		int dark, mid, light;          // main material ramp
		int bandDark, bandLight;       // metal bands
		int interior, glow;            // inside of the chest
		boolean planks;                // horizontal plank seams vs stone blocks
		String[] emblem;               // drawn on the body front, '#'=dark '+'=light '*'=glow
	}

	private static List<Style> styles()
	{
		List<Style> styles = new ArrayList<>();

		Style barrows = new Style();
		barrows.key = "barrows";
		barrows.dark = 0x2B231B; barrows.mid = 0x4A3C2C; barrows.light = 0x66533D;
		barrows.bandDark = 0x3D4A3A; barrows.bandLight = 0x7C8F6E; // tarnished bronze
		barrows.interior = 0x140F0A; barrows.glow = 0xE8C35A;
		barrows.planks = true;
		barrows.emblem = new String[]{
			".#####.",
			"#+++++#",
			"#+#+#+#",
			"#+++++#",
			".#+#+#.",
			"..###..",
		};
		styles.add(barrows);

		Style lunar = new Style();
		lunar.key = "lunar";
		lunar.dark = 0x2E3B57; lunar.mid = 0x4A5E86; lunar.light = 0x7189B5;
		lunar.bandDark = 0x8E9AAE; lunar.bandLight = 0xE3E9F4; // moon silver
		lunar.interior = 0x0D1426; lunar.glow = 0x9FE6FF;
		lunar.planks = false;
		lunar.emblem = new String[]{
			"..###..",
			".#**#..",
			"#**#...",
			"#**#...",
			".#**#..",
			"..###..",
		};
		styles.add(lunar);

		Style cox = new Style();
		cox.key = "cox";
		cox.dark = 0x2C3326; cox.mid = 0x47513B; cox.light = 0x667356;
		cox.bandDark = 0x5C5236; cox.bandLight = 0xA89763;
		cox.interior = 0x0E120B; cox.glow = 0x9CFF6E;
		cox.planks = false;
		cox.emblem = new String[]{
			"...#...",
			"..#*#..",
			".#***#.",
			"#**#**#",
			".#***#.",
			"..#*#..",
			"...#...",
		};
		styles.add(cox);

		Style tob = new Style();
		tob.key = "tob";
		tob.dark = 0x3A1012; tob.mid = 0x5E1A1D; tob.light = 0x84282B;
		tob.bandDark = 0x8C8170; tob.bandLight = 0xE2D7BE; // bone
		tob.interior = 0x170506; tob.glow = 0xFF5050;
		tob.planks = true;
		tob.emblem = new String[]{
			"...#...",
			"..#*#..",
			".#***#.",
			"#*****#",
			"#**+**#",
			".#***#.",
			"..###..",
		};
		styles.add(tob);

		Style toa = new Style();
		toa.key = "toa";
		toa.dark = 0x7A5A2A; toa.mid = 0xB08A45; toa.light = 0xD8B76A; // sandstone
		toa.bandDark = 0x1E4E8C; toa.bandLight = 0x4C8BD6;            // lapis
		toa.interior = 0x2A1A08; toa.glow = 0xFFE27A;
		toa.planks = false;
		toa.emblem = new String[]{
			".###.",
			"#*.*#",
			"#*.*#",
			".#*#.",
			"#***#",
			"..*..",
			"..*..",
		};
		styles.add(toa);
		return styles;
	}

	// ---------------------------------------------------------------- geometry

	private static final class Face
	{
		double[] origin, uAxis, vAxis; // world space
		String part, name;
		double depth;
	}

	private static BufferedImage renderFrame(Style style, double lidAngleDegrees, int frame)
	{
		List<Face> faces = new ArrayList<>();
		double hw = WIDTH / 2, hd = DEPTH / 2;
		// Body box: y 0..BODY_HEIGHT.
		addBox(faces, "body", new double[]{-hw, 0, -hd}, WIDTH, BODY_HEIGHT, DEPTH, 0, null);
		// Lid box sits on the body and rotates around the back-top hinge.
		double[] hinge = {0, BODY_HEIGHT, -hd};
		addBox(faces, "lid", new double[]{-hw, BODY_HEIGHT, -hd}, WIDTH, LID_HEIGHT, DEPTH,
			Math.toRadians(lidAngleDegrees), hinge);

		List<Face> visible = new ArrayList<>();
		for (Face face : faces)
		{
			double[] normal = cross(face.uAxis, face.vAxis);
			if (viewDepth(normal) > 1e-6
				|| (face.part.equals("body") && face.name.equals("top") && lidAngleDegrees > 0))
			{
				double[] center = add(face.origin, add(scale(face.uAxis, 0.5), scale(face.vAxis, 0.5)));
				face.depth = viewDepth(center);
				visible.add(face);
			}
		}
		visible.sort(Comparator.comparingDouble(f -> f.depth));

		int[] pixels = new int[FRAME * FRAME];
		for (Face face : visible)
		{
			rasterize(face, style, pixels, frame);
		}
		outline(pixels);

		BufferedImage image = new BufferedImage(FRAME, FRAME, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, FRAME, FRAME, pixels, 0, FRAME);
		return image;
	}

	/** Adds the six faces of an axis-aligned box, optionally rotated about the X axis at pivot. */
	private static void addBox(List<Face> faces, String part, double[] min, double w, double h, double d,
		double angle, double[] pivot)
	{
		double[] x = {w, 0, 0}, y = {0, h, 0}, z = {0, 0, d};
		double[][] specs = {
			// origin offset, u axis, v axis (u x v points outward)
		};
		addFace(faces, part, "front", add(min, z), x, y, angle, pivot);
		addFace(faces, part, "back", add(min, x), neg(x), y, angle, pivot);
		addFace(faces, part, "right", add(add(min, x), z), neg(z), y, angle, pivot);
		addFace(faces, part, "left", min, z, y, angle, pivot);
		addFace(faces, part, "top", add(add(min, y), z), x, neg(z), angle, pivot);
		addFace(faces, part, "bottom", min, x, z, angle, pivot);
	}

	private static void addFace(List<Face> faces, String part, String name, double[] origin,
		double[] u, double[] v, double angle, double[] pivot)
	{
		Face face = new Face();
		face.part = part;
		face.name = name;
		if (pivot != null && angle != 0)
		{
			face.origin = add(rotateX(sub(origin, pivot), -angle), pivot);
			face.uAxis = rotateX(u, -angle);
			face.vAxis = rotateX(v, -angle);
		}
		else
		{
			face.origin = origin;
			face.uAxis = u;
			face.vAxis = v;
		}
		faces.add(face);
	}

	// Positive angle tips the front of the lid up and back (rotation about +X toward -Z).
	private static double[] rotateX(double[] p, double angle)
	{
		double cos = Math.cos(angle), sin = Math.sin(angle);
		return new double[]{p[0], p[1] * cos - p[2] * sin, p[1] * sin + p[2] * cos};
	}

	private static double[] project(double[] p)
	{
		double x1 = p[0] * Math.cos(YAW) - p[2] * Math.sin(YAW);
		double z1 = p[0] * Math.sin(YAW) + p[2] * Math.cos(YAW);
		double up = p[1] * Math.cos(PITCH) - z1 * Math.sin(PITCH);
		// Centre horizontally, put the bottom-front corner region at y~42.
		return new double[]{21.5 + x1, 34.5 - up};
	}

	private static double viewDepth(double[] p)
	{
		double z1 = p[0] * Math.sin(YAW) + p[2] * Math.cos(YAW);
		return z1 * Math.cos(PITCH) + p[1] * Math.sin(PITCH);
	}

	// ---------------------------------------------------------------- shading

	private static void rasterize(Face face, Style style, int[] pixels, int frame)
	{
		double uLen = length(face.uAxis), vLen = length(face.vAxis);
		int uSteps = (int) Math.ceil(uLen * 3), vSteps = (int) Math.ceil(vLen * 3);
		for (int i = 0; i <= uSteps; i++)
		{
			for (int j = 0; j <= vSteps; j++)
			{
				double u = Math.min(i / (double) uSteps, 0.999);
				double v = Math.min(j / (double) vSteps, 0.999);
				double[] world = add(face.origin, add(scale(face.uAxis, u), scale(face.vAxis, v)));
				double[] screen = project(world);
				int px = (int) Math.floor(screen[0]), py = (int) Math.floor(screen[1]);
				if (px < 0 || py < 0 || px >= FRAME || py >= FRAME) { continue; }
				int color = texture(style, face, u * uLen, v * vLen, uLen, vLen, frame);
				pixels[py * FRAME + px] = 0xFF000000 | color;
			}
		}
	}

	/** Returns RGB for a point at (s, t) pixels along a face of size (w, h). */
	private static int texture(Style style, Face face, double s, double t, double w, double h, int frame)
	{
		int si = (int) Math.floor(s), ti = (int) Math.floor(t);
		int wi = (int) Math.round(w), hi = (int) Math.round(h);
		boolean lid = face.part.equals("lid");
		boolean edge = si == 0 || ti == 0 || si >= wi - 1 || ti >= hi - 1;

		// Interior: the body's top (seen once the lid lifts) and the lid underside.
		if ((face.part.equals("body") && face.name.equals("top")) || (lid && face.name.equals("bottom")))
		{
			double cx = (s / w) - 0.5, cy = (t / h) - 0.5;
			double glow = Math.max(0, 1.0 - Math.sqrt(cx * cx * 1.6 + cy * cy * 3.0) * 1.9);
			if (lid) { glow *= 0.35; }
			glow *= frame >= 3 ? 1.0 : 0.6;
			int c = mix(style.interior, style.glow, glow * glow);
			return edge ? shade(style.dark, 0.7) : c;
		}

		double light;
		switch (face.name)
		{
			case "top": light = 1.18; break;
			case "front": light = 1.0; break;
			case "right": case "left": light = 0.72; break;
			default: light = 0.55;
		}

		// Metal bands wrap vertically around both boxes near each end.
		boolean bandFace = face.name.equals("front") || face.name.equals("back") || face.name.equals("top");
		double bandFromLeft = face.name.equals("back") ? w - s : s;
		boolean band = bandFace && (Math.abs(bandFromLeft - 5) < 1.6 || Math.abs(bandFromLeft - (w - 5)) < 1.6);
		// Rim band along the bottom of the lid and top of the body.
		boolean rim = !face.name.equals("top") && !face.name.equals("bottom")
			&& ((lid && ti == 0) || (!lid && ti >= hi - 1));

		int base;
		if (band || rim)
		{
			boolean highlight = band ? Math.abs(bandFromLeft - 5) < 0.6 || Math.abs(bandFromLeft - (w - 5)) < 0.6 : false;
			base = highlight ? style.bandLight : mix(style.bandDark, style.bandLight, 0.45);
			if (rim) { base = mix(style.bandDark, style.bandLight, 0.3); }
		}
		else
		{
			// Material with seams: planks run horizontally, stone forms offset blocks.
			double gradient = face.name.equals("top") ? 0.5 + 0.4 * (t / h) : 0.25 + 0.6 * (t / h);
			base = mix(style.dark, style.light, Math.min(1, gradient));
			boolean seam;
			if (style.planks)
			{
				seam = !face.name.equals("top") ? ti % 4 == 3 : si % 6 == 5;
			}
			else
			{
				int row = ti / 4;
				seam = ti % 4 == 3 || ((si + (row % 2) * 4) % 9 == 8);
			}
			if (seam) { base = mix(base, style.dark, 0.7); }
			// Deterministic grain noise keeps flat faces from looking vector-drawn.
			int hash = (si * 73856093) ^ (ti * 19349663) ^ face.name.hashCode() ^ (lid ? 0x5bd1e995 : 0);
			if ((hash & 7) == 0) { base = shade(base, 0.9); }
		}

		// Emblem lock plate centred on the body front, just under the rim.
		if (!lid && face.name.equals("front") && style.emblem != null)
		{
			int ew = style.emblem[0].length(), eh = style.emblem.length;
			int ex = (int) Math.floor(s - (w - ew) / 2.0);
			int ey = (int) Math.floor((h - 1.5) - t); // rows counted from the top edge downward
			if (ex >= 0 && ex < ew && ey >= 0 && ey < eh)
			{
				char ch = style.emblem[ey].charAt(ex);
				if (ch == '#') { return shade(style.bandDark, 0.55); }
				if (ch == '+') { return style.bandLight; }
				if (ch == '*') { return frame >= 2 ? mix(style.glow, 0xFFFFFF, 0.25) : style.glow; }
			}
		}

		int color = shade(base, light);
		if (edge) { color = shade(color, 0.82); }
		return color;
	}

	private static void outline(int[] pixels)
	{
		int[] copy = pixels.clone();
		for (int y = 0; y < FRAME; y++)
		{
			for (int x = 0; x < FRAME; x++)
			{
				if (copy[y * FRAME + x] != 0) { continue; }
				boolean touches = (x > 0 && copy[y * FRAME + x - 1] != 0)
					|| (x < FRAME - 1 && copy[y * FRAME + x + 1] != 0)
					|| (y > 0 && copy[(y - 1) * FRAME + x] != 0)
					|| (y < FRAME - 1 && copy[(y + 1) * FRAME + x] != 0);
				if (touches) { pixels[y * FRAME + x] = OUTLINE; }
			}
		}
	}

	// ---------------------------------------------------------------- math & colour

	private static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
	private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
	private static double[] neg(double[] a) { return new double[]{-a[0], -a[1], -a[2]}; }
	private static double[] scale(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
	private static double length(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
	private static double[] cross(double[] a, double[] b)
	{
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static int mix(int a, int b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int shade(int c, double f)
	{
		int r = (int) Math.min(255, Math.round(((c >> 16) & 255) * f));
		int g = (int) Math.min(255, Math.round(((c >> 8) & 255) * f));
		int b = (int) Math.min(255, Math.round((c & 255) * f));
		return (r << 16) | (g << 8) | b;
	}
}
