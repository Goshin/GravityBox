package com.ceco.marshmallow.gravitybox.quicksettings;

import com.ceco.marshmallow.gravitybox.ConnectivityServiceWrapper;
import com.ceco.marshmallow.gravitybox.GravityBoxSettings;
import com.ceco.marshmallow.gravitybox.PhoneWrapper;
import com.ceco.marshmallow.gravitybox.R;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class CellularTile extends AospTile {
    public static final String AOSP_KEY = "cell";
    public static final String MSIM_KEY1 = "cell1";
    public static final String MSIM_KEY2 = "cell2";
    public static final String KEY = "aosp_tile_cell";
    public static final String KEY2 = "aosp_tile_cell2";

    public static enum DataToggle { DISABLED, SINGLEPRESS, LONGPRESS };

    private static final Intent CELLULAR_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.phone", "com.android.phone.MobileNetworkSettings"));

    private String mAospKey;
    private Unhook mCreateTileViewHook;
    private ImageView mDataOffView;
    private TelephonyManager mTm;
    private boolean mDataTypeIconVisible;
    private boolean mIsSignalNull;
    private boolean mDataOffIconEnabled;
    private Unhook mSupportsDualTargetsHook;
    private Unhook mHandleClickHook;
    private boolean mClickHookBlocked;
    private DataToggle mDataToggle;

    protected CellularTile(Object host, String aospKey, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        mAospKey = aospKey;

        if (isPrimary()) {
            mTm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }

        createHooks();
    }

    private boolean isPrimary() {
        return AOSP_KEY.equals(mAospKey) ||
                MSIM_KEY1.equals(mAospKey);
    }

    private boolean isDataTypeIconVisible(Object state) {
        try {
            return (XposedHelpers.getIntField(state, "overlayIconId") != 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isSignalNull(Object info) {
        try {
            boolean noSim = XposedHelpers.getBooleanField(info, "noSim");
            boolean enabled = XposedHelpers.getBooleanField(info, "enabled");
            boolean airplane = XposedHelpers.getBooleanField(info, "airplaneModeEnabled");
            int iconId = 1;
            try {
                iconId = XposedHelpers.getIntField(info, "mobileSignalIconId");
            } catch (Throwable t1) {
                iconId = XposedHelpers.getIntField(info, "mobileSimIconId");
            }
            return (noSim || !enabled || airplane || iconId <= 0);
        } catch (Throwable t2) {
            return false;
        }
    }

    private boolean isMobileDataEnabled() {
        try {
            return (Boolean) XposedHelpers.callMethod(mTm, "getDataEnabled");
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canShowDataOffIcon() {
        return (mDataOffIconEnabled && !mIsSignalNull && !mDataTypeIconVisible);
    }

    private boolean isDualModeEnabled() {
        return (mDualMode && isPrimary());
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();

        mDataOffIconEnabled = mPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_CELL_TILE_DATA_OFF_ICON, false);
        mDataToggle = DataToggle.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_CELL_TILE_DATA_TOGGLE, "DISABLED"));
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CELL_TILE_DATA_OFF_ICON)) {
                mDataOffIconEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_CELL_TILE_DATA_OFF_ICON, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CELL_TILE_DATA_TOGGLE)) {
                mDataToggle = DataToggle.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_CELL_TILE_DATA_TOGGLE));
            }
        }
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.CellularTile";
    }

    @Override
    public String getAospKey() {
        return mAospKey;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        super.onCreateTileView(tileView);

        if (isPrimary() && hasField(tileView, "mIconFrame")) {
            mDataOffView = new ImageView(mContext);
            mDataOffView.setImageDrawable(mGbContext.getDrawable(R.drawable.ic_mobile_data_off));
            mDataOffView.setVisibility(View.GONE);
            FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tileView, "mIconFrame");
            iconFrame.addView(mDataOffView, FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            if (mScalingFactor != 1f) {
                mDataOffView.setScaleX(mScalingFactor);
                mDataOffView.setScaleY(mScalingFactor);
            }
            if (PhoneWrapper.hasMsimSupport()) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mDataOffView.getLayoutParams();
                int marginPx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -4,
                        mContext.getResources().getDisplayMetrics()));
                lp.leftMargin = marginPx;
                lp.topMargin = Math.round(marginPx/2f);
                mDataOffView.setLayoutParams(lp);
            }
        }
    }

    private boolean hasField(Object o, String fieldName) {
        try {
            XposedHelpers.findField(o.getClass(), fieldName);
            return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        super.handleUpdateState(state, arg);

        if (isPrimary() && mDataOffView != null) {
            mDataTypeIconVisible = isDataTypeIconVisible(state);
            mIsSignalNull = isSignalNull(arg);
            if ((Boolean) XposedHelpers.getBooleanField(state, "visible")) {
                final boolean shouldShow = canShowDataOffIcon() && !isMobileDataEnabled();
                mDataOffView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mDataOffView != null) {
                            try {
                                mDataOffView.setVisibility(shouldShow ?
                                    View.VISIBLE : View.GONE);
                            } catch (Throwable t) { /* ignore */ }
                        }
                    }
                });
            }
        }
    }

    private void toggleMobileData() {
        if (!isPrimary()) {
            showDetail();
            return;
        }

        if (mDataOffView != null) {
            // change icon visibility immediately for fast feedback
            final boolean visible = mDataOffView.getVisibility() == View.VISIBLE;
            final boolean shouldShow = !visible && canShowDataOffIcon();
            mDataOffView.post(new Runnable() {
                @Override
                public void run() {
                    if (mDataOffView != null) {
                        mDataOffView.setVisibility(shouldShow ? 
                                View.VISIBLE : View.GONE);
                    }
                }
            });
        }

        // toggle mobile data
        Intent intent = new Intent(ConnectivityServiceWrapper.ACTION_TOGGLE_MOBILE_DATA);
        mContext.sendBroadcast(intent);
    }

    @Override
    public boolean handleLongClick() {
        if (isDualModeEnabled()) {
            if (mDataToggle == DataToggle.LONGPRESS) {
                toggleMobileData();
            } else {
                startSettingsActivity(CELLULAR_SETTINGS);
            }
        } else {
            if (mDataToggle == DataToggle.LONGPRESS) {
                toggleMobileData();
            } else if (mDataToggle != DataToggle.DISABLED){
                return showDetail();
            } else {
                startSettingsActivity(CELLULAR_SETTINGS);
            }
        }
        return true;
    }

    @Override
    public boolean handleSecondaryClick() {
        return (isDualModeEnabled() ? showDetail() : false);
    }

    private boolean showDetail() {
        try {
            mClickHookBlocked = true;
            XposedHelpers.callMethod(mTile, "handleClick");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
        mTm = null;
        mDataOffView = null;
        mDataToggle = null;
    }

    private void createHooks() {
        try {
            mCreateTileViewHook = XposedHelpers.findAndHookMethod(getClassName(), mContext.getClassLoader(),
                    "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        onCreateTileView((View)param.getResult());
                    }
                }
            });

            mSupportsDualTargetsHook = XposedHelpers.findAndHookMethod(BaseTile.CLASS_BASE_TILE, 
                    mContext.getClassLoader(), "supportsDualTargets", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                                param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        param.setResult(isDualModeEnabled());
                    }
                }
            });

            mHandleClickHook = XposedHelpers.findAndHookMethod(getClassName(), mContext.getClassLoader(),
                    "handleClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mClickHookBlocked) {
                        mClickHookBlocked = false;
                    } else {
                        if (mDataToggle == DataToggle.SINGLEPRESS) {
                            toggleMobileData();
                        } else if (!isDualModeEnabled()) {
                            showDetail();
                        }
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
        if (mSupportsDualTargetsHook != null) {
            mSupportsDualTargetsHook.unhook();
            mSupportsDualTargetsHook = null;
        }
        if (mHandleClickHook != null) {
            mHandleClickHook.unhook();
            mHandleClickHook = null;
        }
    }
}
