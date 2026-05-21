package com.ai.pub.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageGenerator {

    public static void generateTestImages(String outputDir) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        generateImage(outputDir + "/banner1.jpg", 800, 400, "Banner");
        generateImage(outputDir + "/product1.jpg", 600, 600, "Product");
        generateImage(outputDir + "/logo.png", 100, 100, "Logo");
        generateImage(outputDir + "/poster.jpg", 600, 800, "Poster");
        generateImage(outputDir + "/bg.jpg", 1200, 800, "Background");
    }

    private static void generateImage(String path, int width, int height, String text) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(new Color(100, 100, 100));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(1, 1, width - 2, height - 2);

        g2d.setColor(new Color(150, 150, 150));
        Font font = new Font("Arial", Font.BOLD, Math.min(width, height) / 10);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        int y = (height - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, x, y);

        String sizeText = width + " x " + height;
        Font smallFont = new Font("Arial", Font.PLAIN, Math.min(width, height) / 20);
        g2d.setFont(smallFont);
        fm = g2d.getFontMetrics();
        x = (width - fm.stringWidth(sizeText)) / 2;
        y = y + fm.getHeight() + 10;
        g2d.drawString(sizeText, x, y);

        g2d.dispose();

        ImageIO.write(image, path.substring(path.lastIndexOf('.') + 1), new File(path));
    }

    public static void main(String[] args) {
        try {
            generateTestImages("/app/uploads/images");
            System.out.println("Test images generated successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
