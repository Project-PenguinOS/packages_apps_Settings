package com.penguin.widget;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import android.os.SystemProperties;

public class DeviceNameTextView extends AppCompatTextView {
    
    private static final String PRODUCT_MARKETNAME_PROP = "ro.product.marketname";

    public DeviceNameTextView(Context context) {
        super(context);
        init(context);
    }

    public DeviceNameTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DeviceNameTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        String deviceName = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.DEVICE_NAME);
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = SystemProperties.get(PRODUCT_MARKETNAME_PROP);
        }
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = Build.MODEL;
        }
        setText(deviceName);
    }
}
