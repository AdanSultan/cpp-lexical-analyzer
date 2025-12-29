package src.main.resources.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class IconGenerator {
    public static void main(String[] args) {
        try {
            // Create icons directory if it doesn't exist
            File iconsDir = new File("src/main/resources/icons");
            if (!iconsDir.exists()) {
                iconsDir.mkdirs();
            }

            // Generate icons
            generateIcon("new", "N", new Color(0, 120, 215));
            generateIcon("open", "O", new Color(0, 120, 215));
            generateIcon("save", "S", new Color(0, 120, 215));
            generateIcon("tokenize", "T", new Color(0, 120, 215));
            generateIcon("parse", "P", new Color(0, 120, 215));
            generateIcon("search", "🔍", new Color(0, 120, 215));
            generateIcon("replace", "R", new Color(0, 120, 215));
            generateIcon("clear", "C", new Color(0, 120, 215));
            generateIcon("export", "E", new Color(0, 120, 215));
            generateIcon("tokens", "T", new Color(0, 120, 215));
            generateIcon("symbols", "S", new Color(0, 120, 215));
            generateIcon("errors", "!", new Color(255, 50, 50));
            generateIcon("console", ">", new Color(0, 120, 215));

            System.out.println("Icons generated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateIcon(String name, String symbol, Color color) throws Exception {
        // Create a 32x32 image
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw background
        g2d.setColor(new Color(240, 240, 250));
        g2d.fillRoundRect(0, 0, 31, 31, 8, 8);

        // Draw border
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(1, 1, 29, 29, 8, 8);

        // Draw symbol
        g2d.setColor(color);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (32 - fm.stringWidth(symbol)) / 2;
        int y = ((32 - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(symbol, x, y);

        g2d.dispose();

        // Save the image
        File output = new File("src/main/resources/icons/" + name + ".png");
        ImageIO.write(image, "png", output);
    }
} 