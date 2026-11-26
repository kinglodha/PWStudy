package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Local reading position for library display, independent of resume-page preference. */
public final class PdfReadingProgress {
    private PdfReadingProgress(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("pdf_reading_progress_v1",Context.MODE_PRIVATE);}
    public static void record(Context c,String id,int page,int total){
        if(id==null||total<=0||page<0||page>=total)return;
        // Position is not proof the document has been completely read.
        int percent=(int)Math.round(100.0*(page+1)/total);
        prefs(c).edit().putInt(id,Math.max(1,Math.min(100,percent))).apply();
    }
    public static int percent(Context c,String id){return Math.max(0,Math.min(100,prefs(c).getInt(id,0)));}
    public static void move(Context c,String oldId,String newId){
        if(!oldId.equals(newId)&&prefs(c).contains(oldId))
            prefs(c).edit().putInt(newId,percent(c,oldId)).remove(oldId).apply();
    }
}
