package com.example.draw;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class InstructionOverlay extends RelativeLayout {
    private TextView textInstruction;
    private TextView headingText;
    private ImageView imageView;

    public InstructionOverlay(Context context) {
        super(context);
        init(context);
    }

    public InstructionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.instruction_overlay, this);
        textInstruction = findViewById(R.id.textInstruction);
        headingText = findViewById(R.id.headingText);
        imageView = findViewById(R.id.imageIcon);
    }

    public void setText(String text) {
        textInstruction.setText(text);
    }

    public void setHeadingText(String text) {
        headingText.setText(text);
    }

    public void setIcon(Drawable icon) {
        imageView.setImageDrawable(icon);
    }
}
