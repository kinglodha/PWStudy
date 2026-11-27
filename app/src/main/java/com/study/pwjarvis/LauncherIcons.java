package com.study.pwjarvis;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** Changes the primary Tweak launcher alias, without disabling the library activity. */
final class LauncherIcons {
    static final String[] NAMES={"Current/default","Matte black infinity","Earth","Aqua infinity","Gold infinity","Silver infinity"};
    private static final String[] ALIASES={"LauncherDefault","LauncherInfinity","LauncherEarth","LauncherAqua","LauncherGold","LauncherSilver"};
    private static final int[] RES={0,R.drawable.tweak_icon_infinity,R.drawable.tweak_icon_earth,R.drawable.tweak_icon_aqua,R.drawable.tweak_icon_gold,R.drawable.tweak_icon_silver};
    private LauncherIcons(){}
    private static ComponentName component(Context context,int i){
        return new ComponentName(context, "com.study.pwjarvis."+ALIASES[i]);
    }
    static int current(Context context){
        PackageManager pm=context.getPackageManager();
        for(int i=0;i<ALIASES.length;i++){
            int state=pm.getComponentEnabledSetting(component(context,i));
            if(state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
               (i==0 && state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))return i;
        }
        return 0;
    }
    static Drawable preview(Context context,int i){
        if(i==0)return context.getApplicationInfo().loadIcon(context.getPackageManager());
        return context.getDrawable(RES[i]);
    }
    static void apply(Context context,int selected){
        if(selected<0||selected>=ALIASES.length)throw new IllegalArgumentException("Unknown icon");
        PackageManager pm=context.getPackageManager();
        int flags=PackageManager.DONT_KILL_APP;
        if(Build.VERSION.SDK_INT>=33){
            List<PackageManager.ComponentEnabledSetting> changes=new ArrayList<>();
            for(int i=0;i<ALIASES.length;i++){
                changes.add(new PackageManager.ComponentEnabledSetting(component(context,i),
                    i==selected?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,flags));
            }
            pm.setComponentEnabledSettings(changes);
        }else{
            int previous=current(context);
            try{
                // Enable first: never intentionally leave the primary launcher unavailable.
                pm.setComponentEnabledSetting(component(context,selected),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,flags);
                for(int i=0;i<ALIASES.length;i++){
                    if(i!=selected)pm.setComponentEnabledSetting(component(context,i),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,flags);
                }
            }catch(RuntimeException failure){
                try{
                    pm.setComponentEnabledSetting(component(context,previous),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,flags);
                    for(int i=0;i<ALIASES.length;i++)if(i!=previous)
                        pm.setComponentEnabledSetting(component(context,i),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,flags);
                }catch(RuntimeException ignored){}
                throw failure;
            }
        }
    }
}
