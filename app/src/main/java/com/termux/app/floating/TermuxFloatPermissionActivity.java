
package com.termux.app.floating;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.termux.R;

public class TermuxFloatPermissionActivity extends Activity {

    private static final int DRAW_OVER_OTHER_APP_PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, DRAW_OVER_OTHER_APP_PERMISSION_REQUEST_CODE);
        } else {
            startTermuxFloatService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DRAW_OVER_OTHER_APP_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startTermuxFloatService();
            } else {
                Toast.makeText(this, R.string.draw_overlay_permission_explanation, Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    private void startTermuxFloatService() {
        Intent intent = new Intent(this, TermuxFloatService.class);
        if (getIntent() != null) {
            intent.setAction(getIntent().getAction());
            intent.setData(getIntent().getData());
            intent.putExtras(getIntent());
        }
        startService(intent);
        finish();
    }
}
