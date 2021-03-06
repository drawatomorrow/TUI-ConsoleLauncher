package ohi.andre.consolelauncher.commands.raw;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecInfo;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class apps implements CommandAbstraction {

    private final String HIDE_PARAM = "-h";
    private final String UNHIDE_PARAM = "-uh";
    private final String SHOWHIDDEN_PARAM = "-sh";
    private final String PLAYSTORE_PARAM = "-ps";
    private final String SETTINGS_PARAM = "-st";
    private final String FORCE_PARAM = "-f";

    @Override
    public String exec(ExecInfo info) {
        String param = info.get(String.class, 0);
        String app = info.get(String.class, 1);
        if (app == null) {
            return info.res.getString(helpRes());
        }

        if (param.equals(HIDE_PARAM)) {
            return hideApp(info, app);
        } else if (param.equals(UNHIDE_PARAM)) {
            return unHideApp(info, app);
        } else if (param.equals(PLAYSTORE_PARAM)) {
            openPlaystore(info.context, app);
            return info.context.getString(R.string.output_showplaystore) + Tuils.SPACE + app;
        } else if (param.equals(SETTINGS_PARAM)) {
            openSettings(info.context, app);
            return info.context.getString(R.string.output_showsettings) + Tuils.SPACE + app;
        } else if (param.equals(FORCE_PARAM)) {
            Intent intent = info.appsManager.getIntent(app);
            info.context.startActivity(intent);
            return info.context.getString(R.string.starting_app) + Tuils.SPACE + app;
        } else {
            return info.res.getString(helpRes());
        }
    }

    private String hideApp(ExecInfo info, String app) {
        SharedPreferences.Editor editor = ((Activity) info.context).getPreferences(0).edit();
        String result = info.appsManager.hideApp(app);
        if (result != null) {
            editor.commit();
            return result + Tuils.SPACE + info.res.getString(R.string.output_hideapp);
        } else
            return info.res.getString(R.string.output_appnotfound);
    }

    private String unHideApp(ExecInfo info, String app) {
        SharedPreferences.Editor editor = ((Activity) info.context).getPreferences(0).edit();
        String result = info.appsManager.unhideApp(app);
        if (result != null) {
            editor.commit();
            return result + Tuils.SPACE + info.res.getString(R.string.output_unhideapp);
        } else
            return info.res.getString(R.string.output_appnotfound);
    }

    private String showHiddenApps(ExecInfo info) {
        return info.appsManager.printApps(AppsManager.HIDDEN_APPS);
    }

    private void openSettings(Context context, String packageName) {
        Tuils.openSettingsPage(context, packageName);
    }

    private void openPlaystore(Context context, String packageName) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
        } catch (Exception e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

    @Override
    public int helpRes() {
        return R.string.help_apps;
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 2;
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.PARAM, CommandAbstraction.PACKAGE};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public String[] parameters() {
        return new String[]{
                HIDE_PARAM,
                UNHIDE_PARAM,
                SHOWHIDDEN_PARAM,
                SETTINGS_PARAM,
                PLAYSTORE_PARAM,
                FORCE_PARAM
        };
    }

    @Override
    public String onNotArgEnough(ExecInfo info, int nArgs) {
        if (nArgs > 0) {
            if (info.get(String.class, 0).equals(SHOWHIDDEN_PARAM))
                return showHiddenApps(info);
            else
                return info.res.getString(helpRes());
        } else
            return info.appsManager.printApps(AppsManager.SHOWN_APPS);
    }

    @Override
    public int notFoundRes() {
        return R.string.output_appnotfound;
    }

}
