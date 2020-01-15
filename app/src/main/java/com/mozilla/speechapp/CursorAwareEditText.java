package com.mozilla.speechapp;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;

public class CursorAwareEditText extends AppCompatEditText {

    private SelectionChangedListener onSelectionChangedListener;

    public CursorAwareEditText(Context context) {
        super(context);
    }

    public CursorAwareEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CursorAwareEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selStart, selEnd);
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    public void setSelectionChangedListener(SelectionChangedListener listener) {
        onSelectionChangedListener = listener;
    }

    public interface SelectionChangedListener {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
