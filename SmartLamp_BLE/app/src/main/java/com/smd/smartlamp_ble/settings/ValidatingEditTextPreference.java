package com.smd.smartlamp_ble.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;

import com.smd.smartlamp_ble.R;

/**
 * Custom EditText preference to validate input <STRONG>AND</STRONG> set the value (if correct) on
 * the text field.
 * Android API <ITALIC>onValidate()</ITALIC> doesn't do the assignment...
 */
public abstract class ValidatingEditTextPreference extends EditTextPreference {
    private String mErrorMessage;

    public ValidatingEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        loadErrorMessage();
    }

    public ValidatingEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadErrorMessage();
    }

    public ValidatingEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadErrorMessage();
    }

    public ValidatingEditTextPreference(Context context) {
        super(context);
        loadErrorMessage();
    }

    public void setErrorMessage(String errorMessage) {
        this.mErrorMessage = errorMessage;
    }

    private void loadErrorMessage() {
        this.mErrorMessage = getContext().getString(R.string.edittext_input_err);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        AlertDialog dlg = (AlertDialog) getDialog();
        View positiveButton = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
        getEditText().setError(null);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPositiveButtonClicked(v);
            }
        });
    }

    private void onPositiveButtonClicked(View v) {
        if (onValidate(getEditText().getText().toString())) {
            getEditText().setError(null);
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
        } else {
            getEditText().setError(mErrorMessage);
            return; // return WITHOUT dismissing the dialog.
        }
    }

    /**
     * Called to validate contents of the edit text.
     *
     * @param text The text to validate.
     * @return true if the value passes validation.
     */
    public boolean onValidate(String text) {
        return false;
    }


}
