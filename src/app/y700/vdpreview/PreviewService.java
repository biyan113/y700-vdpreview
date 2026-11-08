package app.y700.vdpreview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 在主屏上挂一块可拖动、可缩放的小窗，画面来自 y700-agent 的截图。
 * 不创建、不释放虚拟屏。不抢焦点，也不标记 FLAG_SECURE。
 */
public class PreviewService extends Service {
    private static final String PREF = "vd";
    private static final String DISPLAY_NAME = "y700-agent";
    private static final int INK = 0xF01C1917;
    private static final int PAPER = 0xFFF3EFE7;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private ImageView image;
    private TextView title;
    private TextView settingsBtn;
    private TextView shrinkBtn;
    private View grip;
    private boolean minimized;
    private int savedW = 520;
    private int savedH = 980;
    private Bitmap shown;
    private int tick;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshFile = new Runnable() {
        @Override
        public void run() {
            if (image != null && image.getVisibility() == View.VISIBLE) {
                java.io.File file = new java.io.File(getFilesDir(), "live.png");
                if (file.exists() && file.length() > 32) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap);
                        Bitmap old = shown;
                        shown = bitmap;
                        if (old != null && old != bitmap) {
                            old.recycle();
                        }
                    }
                }
            }
            tick++;
            if (tick % 5 == 0) {
                refreshTitle();
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        if (root == null) {
            showWindow();
        }
        handler.removeCallbacks(refreshFile);
        handler.post(refreshFile);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        saveGeometry();
        if (root != null && wm != null) {
            wm.removeView(root);
            root = null;
        }
        if (shown != null) {
            shown.recycle();
            shown = null;
        }
        super.onDestroy();
    }

    private void startInForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                "vd", "虚拟屏", NotificationManager.IMPORTANCE_MIN);
        manager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, "vd")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("虚拟屏")
                .setContentText("浮窗开着，点画面不会点到里面")
                .build();
        startForeground(7, notification);
    }

    private void showWindow() {
        wm = getSystemService(WindowManager.class);
        SharedPreferences pref = prefs();
        savedW = pref.getInt("float_w", 520);
        savedH = pref.getInt("float_h", 980);
        minimized = pref.getBoolean("float_min", false);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        root.setClipToOutline(true);

        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(INK);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(44));
        barLp.gravity = Gravity.TOP;
        root.addView(bar, barLp);
        DragListener drag = new DragListener();
        bar.setOnTouchListener(drag);

        title = new TextView(this);
        title.setText("虚拟屏");
        title.setTextColor(PAPER);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(14), 0, dp(8), 0);
        title.setOnTouchListener(drag);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        settingsBtn = pill("换应用");
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent desk = new Intent(PreviewService.this, Desk.class);
                desk.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(desk);
            }
        });
        bar.addView(settingsBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        shrinkBtn = pill("缩小");
        shrinkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMinimized(!minimized);
            }
        });
        bar.addView(shrinkBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView closeBtn = pill("关闭");
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
        bar.addView(closeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        grip = new Grip();
        FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(dp(36), dp(36));
        gripLp.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(grip, gripLp);
        grip.setOnTouchListener(new ResizeListener());

        lp = new WindowManager.LayoutParams(
                minimized ? dp(230) : savedW,
                minimized ? dp(44) : savedH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = pref.getInt("float_x", 40);
        lp.y = pref.getInt("float_y", 160);
        if (minimized) {
            image.setVisibility(View.GONE);
            grip.setVisibility(View.GONE);
            settingsBtn.setVisibility(View.GONE);
            shrinkBtn.setText("展开");
        }
        wm.addView(root, lp);
        refreshTitle();
    }

    private void refreshTitle() {
        if (title == null) {
            return;
        }
        Display display = findAgentDisplay();
        if (display == null) {
            title.setText("虚拟屏没开");
            return;
        }
        String state = display.getState() == Display.STATE_ON ? "" : "  休眠";
        title.setText("虚拟屏  " + display.getDisplayId() + state);
    }

    private Display findAgentDisplay() {
        DisplayManager manager = getSystemService(DisplayManager.class);
        if (manager == null) {
            return null;
        }
        Display[] displays = manager.getDisplays();
        for (int i = 0; i < displays.length; i++) {
            Display display = displays[i];
            if (display != null && DISPLAY_NAME.equals(display.getName())) {
                return display;
            }
        }
        return null;
    }

    private TextView pill(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(PAPER);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setClickable(true);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setMinimized(boolean on) {
        if (lp == null) {
            return;
        }
        if (on && !minimized) {
            savedW = lp.width;
            savedH = lp.height;
        }
        minimized = on;
        if (on) {
            lp.width = dp(230);
            lp.height = dp(44);
            image.setVisibility(View.GONE);
            grip.setVisibility(View.GONE);
            settingsBtn.setVisibility(View.GONE);
            shrinkBtn.setText("展开");
        } else {
            lp.width = savedW;
            lp.height = savedH;
            image.setVisibility(View.VISIBLE);
            grip.setVisibility(View.VISIBLE);
            settingsBtn.setVisibility(View.VISIBLE);
            shrinkBtn.setText("缩小");
        }
        applyLayout();
        saveGeometry();
    }

    private void applyLayout() {
        if (wm != null && root != null) {
            wm.updateViewLayout(root, lp);
        }
    }

    private void saveGeometry() {
        if (lp == null) {
            return;
        }
        prefs().edit()
                .putInt("float_x", lp.x)
                .putInt("float_y", lp.y)
                .putInt("float_w", minimized ? savedW : lp.width)
                .putInt("float_h", minimized ? savedH : lp.height)
                .putBoolean("float_min", minimized)
                .apply();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    private final class Grip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Grip() {
            super(PreviewService.this);
            GradientDrawable plate = new GradientDrawable();
            plate.setColor(INK);
            float radius = dp(12);
            plate.setCornerRadii(new float[] {radius, radius, 0, 0, 0, 0, 0, 0});
            setBackground(plate);
            paint.setColor(PAPER);
            paint.setStrokeWidth(dp(2));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(10);
            float right = getWidth() - pad;
            float bottom = getHeight() - pad;
            canvas.drawLine(right - dp(12), bottom, right, bottom, paint);
            canvas.drawLine(right, bottom - dp(12), right, bottom, paint);
            canvas.drawLine(right - dp(7), bottom, right, bottom - dp(7), paint);
        }
    }

    private final class DragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float downRawX;
        private float downRawY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x;
                    startY = lp.y;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int nx = startX + (int) (event.getRawX() - downRawX);
                    int ny = startY + (int) (event.getRawY() - downRawY);
                    if (Math.abs(nx - startX) + Math.abs(ny - startY) > dp(3)) {
                        moved = true;
                    }
                    lp.x = nx;
                    lp.y = ny;
                    applyLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        saveGeometry();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class ResizeListener implements View.OnTouchListener {
        private int startW;
        private int startH;
        private float downRawX;
        private float downRawY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startW = lp.width;
                    startH = lp.height;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int w = startW + (int) (event.getRawX() - downRawX);
                    int h = startH + (int) (event.getRawY() - downRawY);
                    if (w < 320) {
                        w = 320;
                    }
                    if (h < 480) {
                        h = 480;
                    }
                    if (w > 1400) {
                        w = 1400;
                    }
                    if (h > 2400) {
                        h = 2400;
                    }
                    lp.width = w;
                    lp.height = h;
                    savedW = w;
                    savedH = h;
                    applyLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                    saveGeometry();
                    return true;
                default:
                    return false;
            }
        }
    }
}
