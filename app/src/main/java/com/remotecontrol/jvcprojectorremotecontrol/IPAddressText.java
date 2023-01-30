package com.remotecontrol.jvcprojectorremotecontrol;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;

/**
 * EditText that only allows input of IP Addresses, using the Phone
 * input type, and automatically inserts periods at the earliest appropriate
 * interval.
 * Source: https://gist.github.com/fingerboxes/5156294
 */

// Note; this probably isn't the best pattern for this - a Factory of Decorator
// pattern would have made more sense, rather than inheritance. However, this
// pattern is consistent with how other android Widgets are invoked, so I went
// with this to prevent confusion

public class IPAddressText extends androidx.appcompat.widget.AppCompatEditText {

    public IPAddressText(Context context) {
        super(context);

        setInputType(InputType.TYPE_CLASS_PHONE);
        setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            if (end > start) {
                String destTxt = dest.toString();
                String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                if (!resultingTxt.matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
                    return "";
                } else {
                    //String[] splits = resultingTxt.split("\\.");
                    String[] splits = resultingTxt.split("\\.");
                    for (String split : splits) {
                        if (Integer.parseInt(split) > 255) {
                            return "";
                        }
                    }
                }
            }
            return null;
        }
        });

        addTextChangedListener(new TextWatcher() {
            boolean deleting = false;
            final int lastCount = 0;

            @Override
            public void afterTextChanged(Editable s) {
                if (!deleting) {
                    String working = s.toString();
                    String[] split = working.split("\\.");
                    String string = split[split.length - 1];
                    if (string.length() == 3 || string.equalsIgnoreCase("0")
                            || (string.length() == 2 && Character.getNumericValue(string.charAt(0)) > 1)) {
                        s.append('.');
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                deleting = lastCount >= count;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing happens here
            }
        });
    }
}