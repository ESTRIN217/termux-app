package com.termux.app.floating;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;

import com.termux.R;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

public class TermuxFloatService extends Service {

    private TermuxFloatView mFloatingWindow;

    private TermuxSession mSession;

    private boolean mVisibleWindow = true;

    private static final String LOG_TAG = "TermuxFloatService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        runStartForeground();
        Logger.logVerbose(LOG_TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        runStartForeground();

        if (mFloatingWindow == null && !initializeFloatView())
            return Service.START_NOT_STICKY;

        String action = null;
        if (intent != null) {
            Logger.logVerboseExtended(LOG_TAG, "Received intent:\n" + IntentUtils.getIntentString(intent));
            action = intent.getAction();
        }

        if (action != null) {
            switch (action) {
                case TERMUX_SERVICE.ACTION_STOP_SERVICE:
                    actionStopService();
                    break;
                case "ACTION_SHOW":
                    setVisible(true);
                    break;
                case "ACTION_HIDE":
                    setVisible(false);
                    break;
                default:
                    Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
                    break;
            }
        } else if (!mVisibleWindow) {
            setVisible(true);
        }

        return Service.START_NOT_STICKY;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logVerbose(LOG_TAG, "onDestroy");

        if (mFloatingWindow != null)
            mFloatingWindow.closeFloatingWindow();

        runStopForeground();
    }
    public void requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service");
        runStopForeground();
        stopSelf();
    }

    private void actionStopService() {
        if (mSession != null)
            mSession.killIfExecuting(this, false);
        requestStopService();
    }

    private void runStartForeground() {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }

    private void runStopForeground() {
        stopForeground(true);
    }



    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    }

    private Notification buildNotification() {
        final Resources res = getResources();

        final String notificationText = res.getString(mVisibleWindow ? R.string.notification_message_visible : R.string.notification_message_hidden);

        final String intentAction = mVisibleWindow ? "ACTION_HIDE" : "ACTION_SHOW";
        Intent notificationIntent = new Intent(this, TermuxFloatService.class).setAction(intentAction);
        PendingIntent contentIntent = PendingIntent.getService(this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder builder =  NotificationUtils.geNotificationBuilder(this,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_LOW,
                "Termux:Float", notificationText, null,
                contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null)  return null;

        builder.setShowWhen(false);

        builder.setSmallIcon(R.mipmap.ic_service_notification);

        builder.setColor(0xFF000000);

        builder.setOngoing(true);

        Intent exitIntent = new Intent(this, TermuxFloatService.class).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit),
                PendingIntent.getService(this, 0, exitIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        return builder.build();
    }



    @SuppressLint("InflateParams")
    private boolean initializeFloatView() {
        boolean floatWindowWasNull = false;
        if (mFloatingWindow == null) {
            mFloatingWindow = (TermuxFloatView) ((LayoutInflater)
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.activity_main, null);
            floatWindowWasNull = true;
        }

        mFloatingWindow.initFloatView(this);

        mSession = createTermuxSession(
                new ExecutionCommand(0, null, null, null, "/data/data/com.termux/files/home", ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false), null);
        if (mSession == null)
            return false;
        mFloatingWindow.getTerminalView().attachSession(mSession.getTerminalSession());

        try {
            mFloatingWindow.launchFloatingWindow();
        } catch (Exception e) {
            Logger.logStackTrace(LOG_TAG, e);
            startActivity(new Intent(this, TermuxFloatPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            requestStopService();
            return false;
        }

        if (floatWindowWasNull)
            Logger.showToast(this, "Tap to show the terminal. Long press and drag to move.", true);

        return true;
    }

    private void setVisible(boolean newVisibility) {
        mVisibleWindow = newVisibility;
        mFloatingWindow.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }



    @Nullable
    public synchronized TermuxSession createTermuxSession(ExecutionCommand executionCommand, String sessionName) {
        if (executionCommand == null) return null;

        Logger.logDebug(LOG_TAG, "Creating \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession");

        if (ExecutionCommand.Runner.APP_SHELL.getName().equals(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring a background execution command passed to createTermuxSession()");
            return null;
        }

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE)
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());

        executionCommand.shellName = sessionName;
        executionCommand.terminalTranscriptRows = 10;
        TermuxSession newTermuxSession = TermuxSession.execute(this, executionCommand,
                mFloatingWindow.getTermuxFloatSessionClient(), null, new TermuxShellEnvironment(),
                null, executionCommand.isPluginExecutionCommand);
        if (newTermuxSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession command for:\n" + executionCommand.getCommandIdAndLabelLogString());
            return null;
        }
        mFloatingWindow.reloadViewStyling();

        return newTermuxSession;
    }

    public TermuxSession getTermuxSession() {
        return mSession;
    }

    public TerminalSession getCurrentSession() {
        return mSession != null ? mSession.getTerminalSession() : null;
    }

}
