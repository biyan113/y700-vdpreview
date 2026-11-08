package app.y700.vdpreview;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 桌面图标打开这一页。虚拟屏本身由 shell 进程拿着，这里不能新建，也不能关掉。
 * 这一页只负责挑选要放到 y700-agent 上的应用，以及显示、收起看画面的浮窗。
 */
public class Desk extends Activity {
    private static final String PREF = "vd";
    private static final String KEY_PKG = "last_pkg";
    private static final String DISPLAY_NAME = "y700-agent";
    private static final int INK = 0xFF1C1917;
    private static final int MUTED = 0xFF6B645C;
    private static final int PAPER = 0xFFF3EFE7;
    private static final int CARD = 0xFFFFFBF6;
    private static final int LINE = 0xFFE4DDD2;
    private static final int ACCENT = 0xFF0F6E56;
    private static final int ACCENT_SOFT = 0xFFE5F3EE;

    private TextView statusView;
    private TextView currentView;
    private ImageView currentIcon;
    private LinearLayout list;
    private final List<String[]> apps = new ArrayList<String[]>();
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().getBooleanExtra("show_float", false)) {
            startForegroundService(new Intent(this, PreviewService.class));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        column.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        column.addView(text("虚拟屏", 28, INK, true));
        TextView lead = text("在主屏旁边看另一块屏幕。点下面的应用，它会在虚拟屏里打开。", 15, MUTED, false);
        lead.setPadding(0, dp(6), 0, dp(16));
        column.addView(lead);

        LinearLayout card = card();
        statusView = text("", 16, INK, true);
        card.addView(statusView);
        LinearLayout current = row();
        current.setPadding(0, dp(12), 0, dp(4));
        currentIcon = new ImageView(this);
        currentIcon.setVisibility(View.GONE);
        current.addView(currentIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        currentView = text("", 15, MUTED, false);
        currentView.setPadding(dp(10), dp(6), 0, 0);
        current.addView(currentView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(current);

        LinearLayout actions = row();
        actions.setPadding(0, dp(14), 0, 0);
        actions.addView(button("打开", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkg = prefs().getString(KEY_PKG, "");
                if (pkg == null || pkg.length() == 0) {
                    statusView.setText("还没选应用。在下面点一个。");
                    return;
                }
                openOnVirtual(pkg, appName(pkg));
            }
        }), weight());
        actions.addView(gap());
        actions.addView(button("看画面", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startForegroundService(new Intent(Desk.this, PreviewService.class));
                statusView.setText("浮窗已显示。拖标题栏移动，右下角缩放。");
            }
        }), weight());
        actions.addView(gap());
        actions.addView(button("收起浮窗", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(Desk.this, PreviewService.class));
                statusView.setText("浮窗已收起。虚拟屏里的应用还在。");
            }
        }), weight());
        card.addView(actions);
        column.addView(card);

        column.addView(promptCard());

        LinearLayout head = row();
        head.setPadding(dp(4), dp(22), dp(4), dp(8));
        head.addView(text("和谁通讯", 18, INK, true), weight());
        TextView refresh = text("刷新", 15, ACCENT, true);
        refresh.setPadding(dp(8), dp(4), dp(4), dp(4));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshList();
            }
        });
        head.addView(refresh);
        column.addView(head);

        EditText search = new EditText(this);
        search.setHint("搜索应用");
        search.setHintTextColor(MUTED);
        search.setTextColor(INK);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setBackground(round(CARD, dp(14)));
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                paintList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        column.addView(search);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, 0);
        column.addView(list);

        apps.addAll(loadApps());
        paintList();
        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void paintList() {
        list.removeAllViews();
        String last = prefs().getString(KEY_PKG, "");
        int shown = 0;
        for (int i = 0; i < apps.size(); i++) {
            final String pkg = apps.get(i)[0];
            final String name = apps.get(i)[1];
            if (query.length() > 0
                    && name.toLowerCase(Locale.ROOT).indexOf(query) < 0
                    && pkg.toLowerCase(Locale.ROOT).indexOf(query) < 0) {
                continue;
            }
            shown++;
            boolean chosen = pkg.equals(last);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(round(chosen ? ACCENT_SOFT : CARD, dp(14)));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(appIcon(pkg));
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(12), 0, dp(8), 0);
            words.addView(text(name, 16, chosen ? ACCENT : INK, chosen));
            TextView sub = text(pkg, 12, MUTED, false);
            sub.setPadding(0, dp(2), 0, 0);
            words.addView(sub);
            row.addView(words, weight());
            if (chosen) {
                row.addView(text("当前", 13, ACCENT, true));
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openOnVirtual(pkg, name);
                    paintList();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            list.addView(row, lp);
        }
        if (shown == 0) {
            String empty = apps.isEmpty()
                    ? "没有列出可打开的应用。点右上角「刷新」再试一次。"
                    : "没有名称里含「" + query + "」的应用。";
            TextView note = text(empty, 15, MUTED, false);
            note.setPadding(dp(8), dp(12), dp(8), dp(8));
            list.addView(note);
        }
    }

    private List<String[]> loadApps() {
        PackageManager pm = getPackageManager();
        List<String[]> found = new ArrayList<String[]>();
        Set<String> seen = new HashSet<String>();
        Intent queryIntent = new Intent(Intent.ACTION_MAIN);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(queryIntent, 0);
        for (int i = 0; i < infos.size(); i++) {
            ResolveInfo info = infos.get(i);
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            found.add(new String[] {pkg, info.loadLabel(pm).toString()});
        }
        if (found.size() < 2) {
            found.clear();
            seen.clear();
            readLauncherFile(new File(getFilesDir(), "launchers.txt"), found, seen);
            readLauncherFile(new File("/data/local/tmp/vd-launchers.txt"), found, seen);
        }
        Collections.sort(found, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return a[1].compareToIgnoreCase(b[1]);
            }
        });
        return found;
    }

    private void readLauncherFile(File file, List<String[]> found, Set<String> seen) {
        if (file == null || !file.isFile() || file.length() < 3) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String pkg = line.substring(0, tab).trim();
                String name = line.substring(tab + 1).trim();
                if (pkg.length() == 0 || name.length() == 0 || getPackageName().equals(pkg) || !seen.add(pkg)) {
                    continue;
                }
                found.add(new String[] {pkg, name});
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void refreshList() {
        statusView.setText("正在刷新应用列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = -1;
                try {
                    Process process = new ProcessBuilder(
                            "/debug_ramdisk/magisk", "su", "-c", "sh /data/local/tmp/vd-launchers.sh")
                            .redirectErrorStream(true)
                            .start();
                    code = process.waitFor();
                } catch (Throwable ignored) {
                    code = -1;
                }
                final int result = code;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == 0) {
                            apps.clear();
                            apps.addAll(loadApps());
                            paintList();
                            refreshStatus();
                        } else {
                            statusView.setText("列表没刷新。下面仍是上次的应用。");
                        }
                    }
                });
            }
        }).start();
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        Display display = findAgentDisplay();
        if (display == null) {
            statusView.setText("虚拟屏没开着");
        } else {
            String state = display.getState() == Display.STATE_ON ? "开着" : "休眠";
            statusView.setText("虚拟屏" + state + "  ·  显示 " + display.getDisplayId()
                    + "  ·  " + display.getMode().getPhysicalWidth()
                    + "×" + display.getMode().getPhysicalHeight());
        }
        String last = prefs().getString(KEY_PKG, "");
        if (last == null || last.length() == 0) {
            currentView.setText("还没选要打开的应用");
            currentIcon.setVisibility(View.GONE);
        } else {
            currentView.setText(appName(last));
            currentIcon.setImageDrawable(appIcon(last));
            currentIcon.setVisibility(View.VISIBLE);
        }
    }

    private void openOnVirtual(String pkg, String name) {
        Display display = findAgentDisplay();
        if (display == null) {
            statusView.setText("虚拟屏没开着，没有把「" + name + "」打开。");
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            statusView.setText("「" + name + "」没有可打开的界面。");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.getDisplayId());
        try {
            startActivity(launch, options.toBundle());
            prefs().edit().putString(KEY_PKG, pkg).apply();
            startForegroundService(new Intent(this, PreviewService.class));
            statusView.setText("已在虚拟屏里打开「" + name + "」。");
            currentView.setText(name);
            currentIcon.setImageDrawable(appIcon(pkg));
            currentIcon.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            statusView.setText("打开「" + name + "」失败。");
        }
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

    private String appName(String pkg) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable t) {
            return getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    private LinearLayout promptCard() {
        LinearLayout box = card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.setLayoutParams(lp);
        box.addView(text("给 code agent", 18, INK, true));
        TextView lead = text("点一次复制全文。贴给别的 code agent，它就按同样的方式操作这块虚拟屏，不会点到主屏。", 14, MUTED, false);
        lead.setPadding(0, dp(6), 0, dp(12));
        box.addView(lead);
        box.addView(button("复制提示词", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyPrompt();
            }
        }));
        final TextView body = text(AgentPrompt.text(findAgentDisplay(), getPackageName()), 13, MUTED, false);
        body.setPadding(0, dp(12), 0, 0);
        body.setTextIsSelectable(true);
        body.setMaxLines(6);
        box.addView(body);
        final TextView toggle = text("展开全文", 14, ACCENT, true);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setOnClickListener(new View.OnClickListener() {
            private boolean open;

            @Override
            public void onClick(View v) {
                open = !open;
                body.setMaxLines(open ? Integer.MAX_VALUE : 6);
                toggle.setText(open ? "收起" : "展开全文");
            }
        });
        box.addView(toggle);
        return box;
    }

    private void copyPrompt() {
        String prompt = AgentPrompt.text(findAgentDisplay(), getPackageName());
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            statusView.setText("没拿到剪贴板，提示词还在下面，可以长按复制。");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("虚拟屏", prompt));
        statusView.setText("提示词已复制。可以直接贴给 code agent。");
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, dp(18)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView view = text(label, 15, primary ? CARD : INK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(primary ? INK : LINE, dp(12)));
        view.setPadding(dp(8), dp(12), dp(8), dp(12));
        view.setOnClickListener(click);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private View gap() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
