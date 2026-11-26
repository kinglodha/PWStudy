package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.*;

/** Device-local history shared by the reader, home and PDF library. */
public final class PdfRecentHistory {
    private PdfRecentHistory() {}
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pdf_manager", Context.MODE_PRIVATE);
    }
    public static List<String> ids(Context c) {
        List<String> ids = new ArrayList<>(prefs(c).getStringSet("recent", Collections.emptySet()));
        ids.sort((a,b) -> Long.compare(time(c,b),time(c,a)));
        return ids;
    }
    public static long time(Context c,String id) {
        return prefs(c).getLong("recent:"+id,0);
    }
    public static String name(Context c,String id) {
        String saved=prefs(c).getString("recent_name:"+id,null);
        if(saved!=null&&!saved.isEmpty())return saved;
        String part=Uri.parse(id).getLastPathSegment();
        if(part==null)return "PDF";
        return part.substring(part.lastIndexOf('/')+1);
    }
    public static void record(Context c,String id,String name) {
        if(id==null||id.isEmpty())return;
        SharedPreferences p=prefs(c);
        Set<String> ids=new HashSet<>(p.getStringSet("recent",Collections.emptySet()));
        ids.add(id);
        p.edit().putStringSet("recent",ids)
            .putString("recent_name:"+id,name)
            .putLong("recent:"+id,System.currentTimeMillis()).apply();
    }
    public static void rename(Context c,String oldId,String newId,String name) {
        SharedPreferences p=prefs(c);
        Set<String> ids=new HashSet<>(p.getStringSet("recent",Collections.emptySet()));
        if(!ids.remove(oldId)&&!ids.contains(newId))return;
        ids.add(newId);
        long last=Math.max(time(c,oldId),time(c,newId));
        p.edit().remove("recent_name:"+oldId).remove("recent:"+oldId)
            .putStringSet("recent",ids).putString("recent_name:"+newId,name)
            .putLong("recent:"+newId,last).apply();
    }
}
