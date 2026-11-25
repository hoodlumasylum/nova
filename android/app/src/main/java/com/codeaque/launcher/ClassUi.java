package com.codeaque.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Big, simple classroom styling — gold on a dark background. */
public final class ClassUi {
    public static final int BG = 0xFF0E1218;
    public static final int CARD = 0xFF1A2230;
    public static final int GOLD = 0xFFE8C872;
    public static final int TEXT = 0xFFF5F0E6;
    public static final int MUTED = 0xFFB8B0A4;
    public static final int GOOD = 0xFF7DCEA0;
    public static final int BAD = 0xFFE07A7A;
    public static final int INK = 0xFF141A22;

    private ClassUi() {}

    public static int dp(Context c, int n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int fill, int stroke, float radius, float strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeW > 0) d.setStroke(Math.max(1, Math.round(strokeW)), stroke);
        return d;
    }

    public static LinearLayout col(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        int p = dp(c, 22);
        col.setPadding(p, p, p, p);
        return col;
    }

    public static ScrollView screen(Context c, View inner) {
        ScrollView sc = new ScrollView(c);
        sc.setFillViewport(true);
        sc.setBackgroundColor(BG);
        sc.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sc.addView(inner, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    public static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(GOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    public static TextView heading(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        t.setPadding(0, dp(c, 8), 0, dp(c, 6));
        return t;
    }

    public static TextView hint(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(0, 0, 0, dp(c, 14));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(0, 0, 0, dp(c, 12));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static Button primary(Context c, String label, View.OnClickListener click) {
        return btn(c, label, GOLD, INK, click);
    }

    public static Button secondary(Context c, String label, View.OnClickListener click) {
        return btn(c, label, CARD, TEXT, click);
    }

    public static Button danger(Context c, String label, View.OnClickListener click) {
        return btn(c, label, 0xFF4A2A2A, 0xFFFFD0D0, click);
    }

    public static Button btn(Context c, String label, int fill, int fg, View.OnClickListener click) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        b.setBackground(round(fill, GOLD, dp(c, 18), fill == CARD ? dp(c, 1) : 0));
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        b.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    public static EditText field(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0x88B8B0A4);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setBackground(round(0xFF10151C, GOLD, dp(c, 14), dp(c, 1)));
        e.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        e.setSingleLine(true);
        e.setInputType(inputType);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        e.setLayoutParams(lp);
        return e;
    }

    public static TextView banner(Context c, String s, boolean on) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(on ? INK : TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(on ? GOOD : 0xFF2A3140, on ? GOOD : GOLD, dp(c, 14), 0));
        t.setPadding(dp(c, 14), dp(c, 16), dp(c, 14), dp(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 16);
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView huge(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        t.setBackground(round(CARD, GOLD, dp(c, 18), dp(c, 2)));
        t.setPadding(dp(c, 18), dp(c, 28), dp(c, 18), dp(c, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 16);
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView codeView(Context c, String code) {
        TextView t = new TextView(c);
        t.setText(code);
        t.setTextColor(GOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(CARD, GOLD, dp(c, 16), dp(c, 2)));
        t.setPadding(dp(c, 12), dp(c, 16), dp(c, 12), dp(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 14);
        t.setLayoutParams(lp);
        t.setLetterSpacing(0.16f);
        return t;
    }

    public static EditText area(Context c, String hint) {
        EditText e = field(c, hint, android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setMaxLines(6);
        e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        return e;
    }

    /** Free for a class teacher and for parents at home. Paid option wording is fixed. */
    public static final String PAID_OPTION =
            "This app is free for a class teacher and for parents at home. "
            + "A paid option is for schools, colleges, universities and other organisations.";

    public static TextView paidHint(Context c) {
        return hint(c, PAID_OPTION);
    }

    public static void addSpace(LinearLayout col, int dps) {
        View v = new View(col.getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(col.getContext(), dps)));
        col.addView(v);
    }
}
