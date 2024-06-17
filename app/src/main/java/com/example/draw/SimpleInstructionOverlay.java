package com.example.draw;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SimpleInstructionOverlay extends RelativeLayout {
    private TextView textInstruction;

    public SimpleInstructionOverlay(Context context) {
        super(context);
        init(context);
    }

    public SimpleInstructionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.simple_instruction_overlay, this, true);
        textInstruction = findViewById(R.id.textInstruction);
    }

    public void setText(String text) {
        textInstruction.setText(text);
    }
}
