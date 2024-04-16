package com.example.draw;

import android.content.Context;
import android.util.Log;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;


public class LineRenderer extends Node {
    private Vector3 start;
    private Vector3 end;
    private Node lineNode;
    private Renderable lineRenderable;
    private Context context;

    public LineRenderer(Context context, Vector3 start, Vector3 end) {
        this.context = context;
        this.start = start;
        this.end = end;
        lineNode = new Node();
        lineNode.setParent(this);
        initLine();
    }

    private void initLine() {
        MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                    float distance = Vector3.subtract(end, start).length();
                    Log.d("LineRenderer", "Creating line with distance: " + distance);
                    lineRenderable = ShapeFactory.makeCube(new Vector3(.01f, .01f, distance),
                            Vector3.zero(), material);
                    lineNode.setRenderable(lineRenderable);
                    updateLine();
                })
                .exceptionally(throwable -> {
                    Log.e("LineRenderer", "Failed to create material", throwable);
                    return null;
                });
    }

    public void updateLine() {
        if (start != null && end != null && lineRenderable != null) {
            Vector3 difference = Vector3.subtract(end, start);
            Vector3 direction = difference.normalized();
            Vector3 center = Vector3.add(start, end).scaled(0.5f);
            Quaternion rotation = Quaternion.lookRotation(direction, Vector3.up());
            lineNode.setWorldPosition(center);
            lineNode.setWorldRotation(rotation);
            float distance = Vector3.subtract(end, start).length();
            lineNode.setLocalScale(new Vector3(.01f, .01f, distance));
            Log.d("LineRenderer", "Updated line position and scale");
        }
    }
}

