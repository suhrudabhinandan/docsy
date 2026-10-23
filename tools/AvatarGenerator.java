package tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class AvatarGenerator {

    private static final int SIZE = 768; // Render at 2x then scale to 384 for supersampling

    public static void main(String[] args) throws Exception {
        File outDir = new File("app/src/main/res/drawable-nodpi");
        outDir.mkdirs();

        for (int i = 1; i <= 17; i++) {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // Draw dark circle backdrop
            double cx = SIZE / 2.0;
            double cy = SIZE / 2.0;
            double r = SIZE * 0.485; // 1.5% inset
            Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);

            g2.setClip(circle);
            g2.setColor(new Color(0x38, 0x3A, 0x3E)); // Charcoal dark backdrop from artwork
            g2.fill(circle);

            // Draw specific avatar subject
            drawAvatarSubject(g2, i, cx, cy, r);

            g2.dispose();

            // Downsample to 384x384 with bilinear filtering
            BufferedImage scaled = new BufferedImage(384, 384, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.drawImage(img, 0, 0, 384, 384, null);
            sg.dispose();

            String name = String.format("avatar_%02d", i);
            File pngFile = new File(outDir, name + ".png");
            ImageIO.write(scaled, "png", pngFile);

            // Convert to webp with quality 92
            File webpFile = new File(outDir, name + ".webp");
            Process p = new ProcessBuilder(
                "convert", pngFile.getAbsolutePath(),
                "-quality", "92",
                "-define", "webp:lossless=false",
                webpFile.getAbsolutePath()
            ).start();
            p.waitFor();
            pngFile.delete(); // Keep only webp
        }
        System.out.println("Generated 17 WebP avatars successfully.");
    }

    private static void drawAvatarSubject(Graphics2D g, int index, double cx, double cy, double r) {
        // Base palette matching monochrome artwork
        Color skinColor = new Color(0xDC, 0xDE, 0xE0);
        Color shadowColor = new Color(0xB5, 0xB7, 0xBA);
        Color hairDark = new Color(0x18, 0x18, 0x1A);
        Color clothDark = new Color(0x22, 0x23, 0x26);
        Color clothWhite = new Color(0xEE, 0xF0, 0xF2);
        Color lineDark = new Color(0x1A, 0x1A, 0x1C);

        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (index) {
            case 1: // Woman in sari with flowing hair
                // Flowing hair behind
                g.setColor(hairDark);
                g.fillOval((int)(cx - r * 0.9), (int)(cy - r * 0.6), (int)(r * 1.8), (int)(r * 1.5));
                // Neck & shoulders
                g.setColor(skinColor);
                g.fillPolygon(new int[]{(int)(cx - 60), (int)(cx + 60), (int)(cx + 100), (int)(cx - 100)},
                              new int[]{(int)(cy + 50), (int)(cy + 50), (int)(cy + 250), (int)(cy + 250)}, 4);
                // Head
                g.fillOval((int)(cx - 90), (int)(cy - 120), 180, 210);
                // Sari drape (white) & blouse (dark)
                g.setColor(clothDark);
                g.fillPolygon(new int[]{(int)(cx - 180), (int)(cx - 50), (int)(cx - 30), (int)(cx - 200)},
                              new int[]{(int)(cy + 180), (int)(cy + 170), (int)(cy + 360), (int)(cy + 360)}, 4);
                g.setColor(clothWhite);
                g.fillPolygon(new int[]{(int)(cx - 40), (int)(cx + 180), (int)(cx + 250), (int)(cx - 100)},
                              new int[]{(int)(cy + 160), (int)(cy + 240), (int)(cy + 360), (int)(cy + 360)}, 4);
                // Hair front
                g.setColor(hairDark);
                g.fillArc((int)(cx - 120), (int)(cy - 160), 240, 240, 30, 200);
                // Face features (gentle closed eyes, smile, pearl earring)
                g.setColor(lineDark);
                g.drawArc((int)(cx - 50), (int)(cy - 20), 35, 20, 10, 160);
                g.drawArc((int)(cx + 15), (int)(cy - 20), 35, 20, 10, 160);
                g.drawArc((int)(cx - 20), (int)(cy + 25), 40, 20, 200, 140);
                g.setColor(clothWhite);
                g.fillOval((int)(cx - 88), (int)(cy + 10), 16, 16); // earring
                break;

            case 2: // Boy with guitar
                g.setColor(hairDark);
                g.fillOval((int)(cx - 110), (int)(cy - 150), 220, 200);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 80), (int)(cy - 90), 160, 190);
                // Hair messy top
                g.setColor(hairDark);
                g.fillArc((int)(cx - 110), (int)(cy - 160), 220, 180, 15, 170);
                // Clothes
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 180), (int)(cy + 120), 360, 250, 40, 40);
                g.setColor(clothWhite);
                g.fillOval((int)(cx - 40), (int)(cy + 110), 80, 90);
                // Guitar body
                g.setColor(clothWhite);
                g.fillOval((int)(cx - 240), (int)(cy + 140), 260, 180);
                g.setColor(lineDark);
                g.fillOval((int)(cx - 140), (int)(cy + 190), 50, 50); // Sound hole
                // Neck of guitar
                g.setColor(shadowColor);
                g.fillPolygon(new int[]{(int)(cx - 100), (int)(cx + 260), (int)(cx + 250), (int)(cx - 110)},
                              new int[]{(int)(cy + 210), (int)(cy + 140), (int)(cy + 160), (int)(cy + 230)}, 4);
                // Face details
                g.setColor(lineDark);
                g.drawArc((int)(cx - 40), (int)(cy - 10), 30, 18, 10, 160);
                g.drawArc((int)(cx + 10), (int)(cy - 10), 30, 18, 10, 160);
                g.drawArc((int)(cx - 15), (int)(cy + 35), 35, 18, 200, 140);
                break;

            case 3: // Boy with headphones and hoodie
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 110), 320, 260, 50, 50); // hoodie
                g.setColor(skinColor);
                g.fillOval((int)(cx - 80), (int)(cy - 90), 160, 190);
                g.setColor(hairDark);
                g.fillOval((int)(cx - 100), (int)(cy - 150), 200, 180);
                // Headphones
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(16f));
                g.drawArc((int)(cx - 115), (int)(cy - 130), 230, 220, 40, 100);
                g.setColor(clothWhite);
                g.fillOval((int)(cx - 125), (int)(cy - 30), 45, 75); // earcup
                g.setColor(lineDark);
                g.fillOval((int)(cx - 118), (int)(cy - 20), 30, 55);
                // Hoodie strings
                g.setColor(clothWhite);
                g.setStroke(new BasicStroke(6f));
                g.drawLine((int)(cx - 30), (int)(cy + 170), (int)(cx - 30), (int)(cy + 270));
                g.drawLine((int)(cx + 30), (int)(cy + 170), (int)(cx + 30), (int)(cy + 270));
                // Eyes & smile
                g.setColor(lineDark);
                g.drawArc((int)(cx - 35), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx + 10), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx - 15), (int)(cy + 35), 32, 18, 200, 140);
                break;

            case 4: // Girl with glasses reading book
                g.setColor(hairDark);
                g.fillOval((int)(cx - 120), (int)(cy - 180), 240, 240); // hair bun
                g.setColor(skinColor);
                g.fillOval((int)(cx - 80), (int)(cy - 80), 160, 180);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 170), (int)(cy + 120), 340, 250, 40, 40);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(7f));
                g.drawOval((int)(cx - 65), (int)(cy - 30), 55, 55);
                g.drawOval((int)(cx + 10), (int)(cy - 30), 55, 55);
                g.drawLine((int)(cx - 10), (int)(cy - 5), (int)(cx + 10), (int)(cy - 5));
                // Open book
                g.setColor(clothWhite);
                g.fillPolygon(new int[]{(int)(cx), (int)(cx - 140), (int)(cx - 130), (int)(cx)},
                              new int[]{(int)(cy + 190), (int)(cy + 160), (int)(cy + 270), (int)(cy + 300)}, 4);
                g.fillPolygon(new int[]{(int)(cx), (int)(cx + 140), (int)(cx + 130), (int)(cx)},
                              new int[]{(int)(cy + 190), (int)(cy + 160), (int)(cy + 270), (int)(cy + 300)}, 4);
                // Hands
                g.setColor(skinColor);
                g.fillOval((int)(cx - 130), (int)(cy + 220), 45, 50);
                g.fillOval((int)(cx + 85), (int)(cy + 220), 45, 50);
                break;

            case 5: // Girl with camera
                g.setColor(hairDark);
                g.fillOval((int)(cx - 110), (int)(cy - 170), 220, 220);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 70), 150, 170);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 130), 320, 240, 40, 40);
                // Camera held up
                g.setColor(lineDark);
                g.fillRoundRect((int)(cx + 10), (int)(cy + 20), 160, 110, 15, 15);
                g.setColor(shadowColor);
                g.fillOval((int)(cx + 50), (int)(cy + 35), 70, 70); // Lens
                g.setColor(lineDark);
                g.fillOval((int)(cx + 65), (int)(cy + 50), 40, 40);
                // Hands holding camera
                g.setColor(skinColor);
                g.fillOval((int)(cx + 5), (int)(cy + 45), 45, 65);
                g.fillOval((int)(cx + 135), (int)(cy + 60), 40, 60);
                // Closed eye & smile
                g.drawArc((int)(cx - 45), (int)(cy - 5), 30, 18, 10, 160);
                g.drawArc((int)(cx - 20), (int)(cy + 35), 30, 16, 200, 140);
                break;

            case 6: // Boy with cap and backpack
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 120), 320, 250, 40, 40);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 60), 150, 170);
                // Cap
                g.setColor(lineDark);
                g.fillArc((int)(cx - 95), (int)(cy - 140), 190, 150, 0, 180);
                g.fillPolygon(new int[]{(int)(cx - 95), (int)(cx + 140), (int)(cx + 120), (int)(cx - 80)},
                              new int[]{(int)(cy - 65), (int)(cy - 45), (int)(cy - 30), (int)(cy - 50)}, 4); // visor
                // Backpack strap
                g.setColor(shadowColor);
                g.fillRoundRect((int)(cx - 140), (int)(cy + 130), 38, 180, 10, 10);
                // Face details
                g.setColor(lineDark);
                g.drawArc((int)(cx - 35), (int)(cy + 5), 28, 16, 10, 160);
                g.drawArc((int)(cx + 12), (int)(cy + 5), 28, 16, 10, 160);
                g.drawArc((int)(cx - 10), (int)(cy + 45), 30, 16, 200, 140);
                break;

            case 7: // Girl with headphones and hot mug
                g.setColor(hairDark);
                g.fillOval((int)(cx - 110), (int)(cy - 150), 220, 240);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 75), 150, 175);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 125), 320, 245, 40, 40);
                // Headphones
                g.setColor(clothWhite);
                g.fillOval((int)(cx - 120), (int)(cy - 25), 45, 75);
                g.setColor(lineDark);
                g.fillOval((int)(cx - 113), (int)(cy - 15), 30, 55);
                // Steaming mug held in hands
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx + 25), (int)(cy + 155), 75, 90, 15, 15);
                g.drawArc((int)(cx + 90), (int)(cy + 170), 30, 40, 270, 180); // handle
                // Steam curves
                g.setColor(clothWhite);
                g.drawArc((int)(cx + 45), (int)(cy + 110), 25, 35, 10, 180);
                // Face details
                g.setColor(lineDark);
                g.drawArc((int)(cx - 35), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx + 10), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx - 12), (int)(cy + 30), 32, 16, 200, 140);
                break;

            case 8: // Boy with laptop and books
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 120), 320, 250, 40, 40);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 85), 150, 175);
                g.setColor(hairDark);
                g.fillOval((int)(cx - 95), (int)(cy - 145), 190, 150);
                // Laptop on desk
                g.setColor(shadowColor);
                g.fillPolygon(new int[]{(int)(cx - 30), (int)(cx + 200), (int)(cx + 220), (int)(cx - 10)},
                              new int[]{(int)(cy + 180), (int)(cy + 180), (int)(cy + 320), (int)(cy + 320)}, 4);
                // Books
                g.setColor(clothDark);
                g.fillRect((int)(cx - 200), (int)(cy + 240), 120, 30);
                g.setColor(clothWhite);
                g.fillRect((int)(cx - 190), (int)(cy + 215), 115, 25);
                // Hand on cheek
                g.setColor(skinColor);
                g.fillOval((int)(cx + 45), (int)(cy + 10), 45, 60);
                // Face details
                g.setColor(lineDark);
                g.drawArc((int)(cx - 35), (int)(cy - 15), 28, 16, 10, 160);
                g.drawArc((int)(cx + 8), (int)(cy - 15), 28, 16, 10, 160);
                g.drawArc((int)(cx - 10), (int)(cy + 25), 30, 16, 200, 140);
                break;

            case 9: // Boy with book and mug
                g.setColor(hairDark);
                g.fillOval((int)(cx - 100), (int)(cy - 150), 200, 170);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 80), 150, 175);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 170), (int)(cy + 120), 340, 250, 40, 40);
                // Book held in hand
                g.setColor(clothWhite);
                g.fillPolygon(new int[]{(int)(cx + 20), (int)(cx + 160), (int)(cx + 150), (int)(cx + 10)},
                              new int[]{(int)(cy + 190), (int)(cy + 170), (int)(cy + 290), (int)(cy + 300)}, 4);
                // Mug in other hand
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 40), (int)(cy + 170), 50, 65, 10, 10);
                // Face
                g.setColor(lineDark);
                g.drawArc((int)(cx - 35), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx + 10), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx - 12), (int)(cy + 30), 30, 16, 200, 140);
                break;

            case 10: // Girl with glasses at desk with laptop
                g.setColor(hairDark);
                g.fillOval((int)(cx - 120), (int)(cy - 170), 240, 240);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 70), 150, 170);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 130), 320, 240, 40, 40);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(7f));
                g.drawOval((int)(cx - 65), (int)(cy - 20), 55, 55);
                g.drawOval((int)(cx + 10), (int)(cy - 20), 55, 55);
                g.drawLine((int)(cx - 10), (int)(cy + 5), (int)(cx + 10), (int)(cy + 5));
                // Desk items
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx - 180), (int)(cy + 230), 70, 85, 10, 10); // mug
                g.drawArc((int)(cx - 15), (int)(cy + 40), 30, 16, 200, 140);
                break;

            case 11: // Man with beard, glasses and mug
                g.setColor(hairDark);
                g.fillOval((int)(cx - 105), (int)(cy - 160), 210, 180);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 80), 150, 180);
                // Beard
                g.setColor(hairDark);
                g.fillRoundRect((int)(cx - 60), (int)(cy + 25), 120, 90, 30, 30);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 170), (int)(cy + 140), 340, 230, 40, 40);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(7f));
                g.drawOval((int)(cx - 60), (int)(cy - 25), 50, 50);
                g.drawOval((int)(cx + 10), (int)(cy - 25), 50, 50);
                g.drawLine((int)(cx - 10), (int)(cy), (int)(cx + 10), (int)(cy));
                // Mug
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx + 60), (int)(cy + 170), 65, 80, 10, 10);
                break;

            case 12: // Elder woman with glasses and sari
                g.setColor(new Color(0xD0, 0xD2, 0xD5)); // grey hair
                g.fillOval((int)(cx - 120), (int)(cy - 170), 240, 230);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 70), 150, 175);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(6f));
                g.drawOval((int)(cx - 60), (int)(cy - 20), 50, 50);
                g.drawOval((int)(cx + 10), (int)(cy - 20), 50, 50);
                // Sari
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 170), (int)(cy + 130), 340, 240, 40, 40);
                g.setColor(clothWhite);
                g.fillPolygon(new int[]{(int)(cx - 60), (int)(cx + 170), (int)(cx + 200), (int)(cx - 100)},
                              new int[]{(int)(cy + 160), (int)(cy + 220), (int)(cy + 360), (int)(cy + 360)}, 4);
                // Bindi & smile
                g.setColor(lineDark);
                g.fillOval((int)(cx - 5), (int)(cy - 35), 10, 10);
                g.drawArc((int)(cx - 15), (int)(cy + 40), 30, 16, 200, 140);
                break;

            case 13: // Girl with DSA / System Design books coding
                g.setColor(hairDark);
                g.fillOval((int)(cx - 110), (int)(cy - 170), 220, 220);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 70), 150, 170);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 130), 320, 240, 40, 40);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(7f));
                g.drawOval((int)(cx - 65), (int)(cy - 20), 55, 55);
                g.drawOval((int)(cx + 10), (int)(cy - 20), 55, 55);
                // Stack of books
                g.setColor(clothDark);
                g.fillRect((int)(cx - 200), (int)(cy + 200), 140, 30);
                g.fillRect((int)(cx - 195), (int)(cy + 235), 135, 30);
                g.fillRect((int)(cx - 190), (int)(cy + 270), 130, 30);
                // Laptop on desk
                g.setColor(shadowColor);
                g.fillPolygon(new int[]{(int)(cx + 40), (int)(cx + 220), (int)(cx + 210), (int)(cx + 30)},
                              new int[]{(int)(cy + 160), (int)(cy + 170), (int)(cy + 320), (int)(cy + 320)}, 4);
                break;

            case 14: // Boy in gym with shaker
                g.setColor(hairDark);
                g.fillOval((int)(cx - 100), (int)(cy - 160), 200, 180);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 80), 150, 180);
                // Muscular shoulders / tank top
                g.setColor(skinColor);
                g.fillOval((int)(cx - 180), (int)(cy + 120), 140, 180); // arm
                g.fillOval((int)(cx + 40), (int)(cy + 120), 140, 180);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 90), (int)(cy + 130), 180, 240, 30, 30); // tank top
                // Towel around neck
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx - 95), (int)(cy + 90), 50, 150, 20, 20);
                g.fillRoundRect((int)(cx + 45), (int)(cy + 90), 50, 150, 20, 20);
                // Shaker bottle
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 30), (int)(cy + 220), 60, 110, 15, 15);
                break;

            case 15: // Elder man with white beard and mug
                g.setColor(new Color(0xD5, 0xD8, 0xDC)); // white hair
                g.fillOval((int)(cx - 110), (int)(cy - 160), 220, 200);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 80), 150, 180);
                // White beard
                g.setColor(new Color(0xD5, 0xD8, 0xDC));
                g.fillRoundRect((int)(cx - 65), (int)(cy + 20), 130, 100, 35, 35);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 170), (int)(cy + 135), 340, 235, 40, 40);
                // Glasses
                g.setColor(lineDark);
                g.setStroke(new BasicStroke(6f));
                g.drawOval((int)(cx - 60), (int)(cy - 20), 50, 50);
                g.drawOval((int)(cx + 10), (int)(cy - 20), 50, 50);
                // Mug
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx + 45), (int)(cy + 180), 65, 80, 10, 10);
                break;

            case 16: // Boy singing with mic and guitar
                g.setColor(hairDark);
                g.fillOval((int)(cx - 110), (int)(cy - 160), 220, 190);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 80), 150, 180);
                g.setColor(clothDark);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 130), 320, 240, 40, 40);
                // Singing open mouth
                g.setColor(lineDark);
                g.fillOval((int)(cx - 20), (int)(cy + 25), 40, 30);
                // Microphone on stand
                g.setColor(shadowColor);
                g.fillOval((int)(cx + 50), (int)(cy + 5), 45, 55);
                g.setColor(lineDark);
                g.fillRect((int)(cx + 70), (int)(cy + 60), 10, 120);
                // Music notes
                g.drawString("♪", (int)(cx + 120), (int)(cy - 50));
                g.drawString("♫", (int)(cx + 90), (int)(cy - 90));
                break;

            case 17: // Boy studying with notebook and pen
                g.setColor(hairDark);
                g.fillOval((int)(cx - 105), (int)(cy - 150), 210, 190);
                g.setColor(skinColor);
                g.fillOval((int)(cx - 75), (int)(cy - 75), 150, 175);
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx - 160), (int)(cy + 130), 320, 240, 40, 40); // white hoodie
                // Desk & notebook
                g.setColor(clothWhite);
                g.fillRoundRect((int)(cx - 120), (int)(cy + 220), 240, 90, 10, 10);
                // Pen in hand
                g.setColor(skinColor);
                g.fillOval((int)(cx - 20), (int)(cy + 180), 45, 55);
                g.setColor(lineDark);
                g.drawLine((int)(cx), (int)(cy + 185), (int)(cx + 25), (int)(cy + 235));
                // Face details
                g.drawArc((int)(cx - 35), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx + 10), (int)(cy - 10), 28, 16, 10, 160);
                g.drawArc((int)(cx - 12), (int)(cy + 30), 32, 16, 200, 140);
                break;
        }
    }
}
