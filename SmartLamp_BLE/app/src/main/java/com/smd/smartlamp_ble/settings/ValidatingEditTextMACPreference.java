package com.smd.smartlamp_ble.settings;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * Custom EditText preference to validate input <STRONG>AND</STRONG> set the value (if correct) on
 * the text field.
 * Android API <ITALIC>onValidate()</ITALIC> doesn't do the assignment...
 */
public class ValidatingEditTextMACPreference extends ValidatingEditTextPreference {
    public ValidatingEditTextMACPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setMACFilter();
    }

    public ValidatingEditTextMACPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setMACFilter();
    }

    public ValidatingEditTextMACPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMACFilter();
    }

    public ValidatingEditTextMACPreference(Context context) {
        super(context);
        setMACFilter();
    }

    /**
     *
     */
    public void setMACFilter() {
        final EditText input = getEditText();
        input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setSingleLine();
        InputFilter[] filters = new InputFilter[1];

        filters[0] = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) +
                            destTxt.substring(dend);
                    if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,5}[0-9a-fA-F]")) {
                    } else if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,4}[0-9a-fA-F][0-9a-fA-F]")) {
                        return source.subSequence(start, end) + ":";
                    } else if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,5}[0-9a-fA-F][0-9a-fA-F]")) {
                    }
                }
                return null;
            }
        };
        input.setFilters(filters);
    }

    /**
     * Called to validate contents of the edit text as a MAC address, as per standard (IEEE 802)
     * format for printing MAC-48 addresses in human-friendly form: six groups of two hexadecimal digits,
     * separated by hyphens - or colons :.
     *
     * @param text The text to validate.
     * @return true if the value passes validation.
     */
    @Override
    public boolean onValidate(String text) {
        return text.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }
}
