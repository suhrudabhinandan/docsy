package tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class IconGenerator {

    private static final int MASTER_SIZE = 768;

    public static void main(String[] args) throws Exception {
        BufferedImage masterSquare = renderMaster(false);
        BufferedImage masterRound = renderMaster(true);

        int[][] densities = {
            {48, 1},  // mdpi
            {72, 1},  // hdpi
            {96, 1},  // xhdpi
            {144, 1}, // xxhdpi
            {192, 1}  // xxxhdpi
        };
        String[] folderNames = {
            "mipmap-mdpi",
            "mipmap-hdpi",
            "mipmap-xhdpi",
            "mipmap-xxhdpi",
            "mipmap-xxxhdpi"
        };

        File resDir = new File("app/src/main/res");

        for (int i = 0; i < densities.length; i++) {
            int size = densities[i][0];
            String folder = folderNames[i];
            File dir = new File(resDir, folder);
            dir.mkdirs();

            // Delete old webp files to avoid AAPT duplicate conflicts
            new File(dir, "ic_launcher.webp").delete();
            new File(dir, "ic_launcher_round.webp").delete();

            // Scale and save square launcher
            BufferedImage scaledSquare = scale(masterSquare, size, size);
            ImageIO.write(scaledSquare, "png", new File(dir, "ic_launcher.png"));

            // Scale and save round launcher
            BufferedImage scaledRound = scale(masterRound, size, size);
            ImageIO.write(scaledRound, "png", new File(dir, "ic_launcher_round.png"));

            System.out.println("Generated icons for " + folder + " (" + size + "x" + size + ")");
        }
    }

    private static BufferedImage renderMaster(boolean isRound) {
        BufferedImage img = new BufferedImage(MASTER_SIZE, MASTER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double w = MASTER_SIZE;
        double h = MASTER_SIZE;

        // Clip to shape
        Shape clipShape;
        if (isRound) {
            clipShape = new Ellipse2D.Double(8, 8, w - 16, h - 16);
        } else {
            // Material squircle / rounded rect for legacy square launcher
            clipShape = new RoundRectangle2D.Double(8, 8, w - 16, h - 16, 160, 160);
        }
        g2.setClip(clipShape);

        // Background Gradient (#2563EB -> #1E3A8A)
        GradientPaint bgPaint = new GradientPaint(
            0, 0, new Color(0x25, 0x63, 0xEB),
            (float) w, (float) h, new Color(0x1E, 0x3A, 0x8A)
        );
        g2.setPaint(bgPaint);
        g2.fill(clipShape);

        // Subtle ambient radial glow behind central sheet
        RadialGradientPaint glow = new RadialGradientPaint(
            (float)(w / 2.0), (float)(h / 2.0), (float)(w * 0.45),
            new float[]{0f, 1f},
            new Color[]{new Color(255, 255, 255, 40), new Color(255, 255, 255, 0)}
        );
        g2.setPaint(glow);
        g2.fill(clipShape);

        // Scaling factor from 108dp viewport to MASTER_SIZE
        double scale = MASTER_SIZE / 108.0;

        // 1. Back sheet (secondary stacked document for depth)
        g2.setColor(new Color(219, 234, 254, 70));
        RoundRectangle2D backDoc = new RoundRectangle2D.Double(
            31 * scale, 35 * scale, (67 - 31) * scale, (81 - 35) * scale, 16 * scale, 16 * scale
        );
        g2.fill(backDoc);

        // 2. Main document sheet with folded corner
        Path2D mainDoc = new Path2D.Double();
        mainDoc.moveTo(41 * scale, 32 * scale);
        mainDoc.lineTo(65 * scale, 32 * scale);
        mainDoc.lineTo(75 * scale, 42 * scale);
        mainDoc.lineTo(75 * scale, 80 * scale);
        mainDoc.curveTo(75 * scale, 82.2 * scale, 73.2 * scale, 84 * scale, 71 * scale, 84 * scale);
        mainDoc.lineTo(41 * scale, 84 * scale);
        mainDoc.curveTo(38.8 * scale, 84 * scale, 37 * scale, 82.2 * scale, 37 * scale, 80 * scale);
        mainDoc.lineTo(37 * scale, 36 * scale);
        mainDoc.curveTo(37 * scale, 33.8 * scale, 38.8 * scale, 32 * scale, 41 * scale, 32 * scale);
        mainDoc.closePath();

        // Drop shadow for main document
        g2.setColor(new Color(0, 0, 0, 45));
        AffineTransform txShadow = AffineTransform.getTranslateInstance(0, 4 * scale);
        Shape shadowDoc = txShadow.createTransformedShape(mainDoc);
        g2.fill(shadowDoc);

        // Fill main document in crisp pure white
        g2.setColor(Color.WHITE);
        g2.fill(mainDoc);

        // Folded corner flap
        Path2D flap = new Path2D.Double();
        flap.moveTo(65 * scale, 32 * scale);
        flap.lineTo(65 * scale, 42 * scale);
        flap.lineTo(75 * scale, 42 * scale);
        flap.closePath();
        g2.setColor(new Color(0xDB, 0xEA, 0xFE));
        g2.fill(flap);

        // Flap fold crease line
        g2.setColor(new Color(0xBF, 0xDB, 0xFE));
        g2.setStroke(new BasicStroke((float)(1.5 * scale)));
        g2.draw(flap);

        // Text lines inside document
        // Line 1: Primary heading (Royal Blue)
        g2.setColor(new Color(0x25, 0x63, 0xEB));
        g2.setStroke(new BasicStroke((float)(3.5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(44 * scale, 46 * scale, 60 * scale, 46 * scale));

        // Lines 2-4: Light blue body lines
        g2.setColor(new Color(0x93, 0xC5, 0xFD));
        g2.setStroke(new BasicStroke((float)(2.5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(44 * scale, 53 * scale, 68 * scale, 53 * scale));
        g2.draw(new Line2D.Double(44 * scale, 59 * scale, 68 * scale, 59 * scale));
        g2.draw(new Line2D.Double(44 * scale, 65 * scale, 56 * scale, 65 * scale));

        // Assistant Badge in bottom-right corner
        double badgeCx = 67 * scale;
        double badgeCy = 71 * scale;
        double badgeR = 9.5 * scale;

        // Badge shadow
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fill(new Ellipse2D.Double(badgeCx - badgeR, badgeCy - badgeR + 3 * scale, 2 * badgeR, 2 * badgeR));

        // Badge circle fill
        g2.setColor(new Color(0x1D, 0x4E, 0xD8));
        g2.fill(new Ellipse2D.Double(badgeCx - badgeR, badgeCy - badgeR, 2 * badgeR, 2 * badgeR));

        // Badge white border
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke((float)(1.5 * scale)));
        g2.draw(new Ellipse2D.Double(badgeCx - badgeR, badgeCy - badgeR, 2 * badgeR, 2 * badgeR));

        // 4-pointed golden sparkle star in badge
        Path2D star = new Path2D.Double();
        star.moveTo(badgeCx, badgeCy - 6 * scale);
        star.quadTo(badgeCx, badgeCy, badgeCx + 6 * scale, badgeCy);
        star.quadTo(badgeCx, badgeCy, badgeCx, badgeCy + 6 * scale);
        star.quadTo(badgeCx, badgeCy, badgeCx - 6 * scale, badgeCy);
        star.quadTo(badgeCx, badgeCy, badgeCx, badgeCy - 6 * scale);
        star.closePath();
        g2.setColor(new Color(0xFB, 0xBF, 0x24));
        g2.fill(star);

        // Accent small sparkle
        double miniCx = 74 * scale;
        double miniCy = 55 * scale;
        Path2D miniStar = new Path2D.Double();
        miniStar.moveTo(miniCx, miniCy - 3.5 * scale);
        miniStar.quadTo(miniCx, miniCy, miniCx + 3.5 * scale, miniCy);
        miniStar.quadTo(miniCx, miniCy, miniCx, miniCy + 3.5 * scale);
        miniStar.quadTo(miniCx, miniCy, miniCx - 3.5 * scale, miniCy);
        miniStar.quadTo(miniCx, miniCy, miniCx, miniCy - 3.5 * scale);
        miniStar.closePath();
        g2.setColor(new Color(0xFD, 0xE0, 0x47));
        g2.fill(miniStar);

        g2.dispose();
        return img;
    }

    private static BufferedImage scale(BufferedImage src, int targetW, int targetH) {
        BufferedImage dest = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dest;
    }
}
