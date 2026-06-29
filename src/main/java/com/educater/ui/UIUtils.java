package com.educater.ui;

import javafx.stage.Stage;
import javafx.scene.image.Image;

public class UIUtils {
    public static void setIcon(Stage stage) {
        try {
            stage.getIcons().add(new Image(UIUtils.class.getResourceAsStream("/images/educaster.png")));
        } catch (Exception ignored) {
            // Ignore if icon is missing or fails to load
        }
    }
}
