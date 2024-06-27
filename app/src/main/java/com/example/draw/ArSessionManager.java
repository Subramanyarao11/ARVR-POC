package com.example.draw;

import com.google.ar.core.Session;
import com.google.ar.sceneform.Scene;

public class ArSessionManager {
    private static ArSessionManager instance;
    private Session arSession;
    private Scene arScene;

    private ArSessionManager() { }

    public static synchronized ArSessionManager getInstance() {
        if (instance == null) {
            instance = new ArSessionManager();
        }
        return instance;
    }

    public void initialize(Session session, Scene scene) {
        this.arSession = session;
        this.arScene = scene;
    }

    public Session getArSession() {
        return arSession;
    }

    public Scene getArScene() {
        return arScene;
    }
}
