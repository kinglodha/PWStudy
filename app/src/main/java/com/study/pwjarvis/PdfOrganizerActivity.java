package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class PdfOrganizerActivity extends Activity {
    private static final int PICK_TREE=5012;
    private LinearLayout content;
    private TextView status;
    private Uri treeUri;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean scanning=false;
    private static class Entry {
        Uri uri; String oldName, newName, subject, relative;
        Entry(Uri u,String old,String name,String subject,String relative) {
            this.uri=u; this.oldName=old; this.newName=name; this.subject=subject; this.relative=relative;
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff111111);
        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(1); content.setPadding(24,24,24,24);
        scroll.addView(content); setContentView(scroll);
        title("AI PDF Organizer — Offline Preview");
        status=new TextView(this); status.setText("Choose your A PDF folder. No files will be modified.");
        content.addView(status);
        Button choose=button("Choose PDF Folder"); choose.setOnClickListener(v->chooseFolder());
        Button scan=button("Scan & Preview (read-only)"); scan.setOnClickListener(v->startScan());
        Button ai=button("ChatGPT AI Review (not configured)");
        ai.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Optional AI review")
            .setMessage("AI review needs an explicit opt-in, a secure backend and API billing. No PDF data is sent by this version. Offline scan works without internet.")
            .setPositiveButton("OK",null).show());
        content.addView(choose); content.addView(scan); content.addView(ai);
        Uri saved=null;
        String value=getPreferences(MODE_PRIVATE).getString("tree",null);
        if(value!=null) {try {saved=Uri.parse(value);}catch(Exception ignored){}}
        treeUri=saved;
        if(saved!=null)status.setText("Folder selected. Tap Scan & Preview.");
    }
    private void title(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(22);t.setPadding(0,8,0,20);content.addView(t);}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void chooseFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_TREE||result!=RESULT_OK||data==null||data.getData()==null)return;
        treeUri=data.getData();
        try {getContentResolver().takePersistableUriPermission(treeUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(SecurityException ex){status.setText("Permission not saved: "+ex.getMessage());return;}
        getPreferences(MODE_PRIVATE).edit().putString("tree",treeUri.toString()).apply();
        status.setText("Folder selected. Ready for read-only scan.");
    }
    private void startScan(){
        if(scanning||treeUri==null){if(treeUri==null)status.setText("Please choose A PDF folder first.");return;}
        scanning=true; entries.clear(); status.setText("Scanning...");
        worker.execute(()->{
            String error=null;
            try {
                Uri root=DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
                HashSet<String> visited=new HashSet<>();
                walk(root,"",visited);
            }catch(Exception ex){error=ex.getClass().getSimpleName()+": "+ex.getMessage();}
            final String finalError=error;
            runOnUiThread(()->{scanning=false;render(finalError);});
        });
    }
    private void walk(Uri folder,String path,Set<String> visited){
        String id=DocumentsContract.getDocumentId(folder);
        if(!visited.add(id)||visited.size()>10000)return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,id);
        try(Cursor cursor=getContentResolver().query(children,new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null)return;
            while(cursor.moveToNext()){
                String childId=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                if(name==null)continue;
                Uri uri=DocumentsContract.buildDocumentUriUsingTree(treeUri,childId);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                    walk(uri,path+name+"/",visited);
                }else if(name.toLowerCase(Locale.ROOT).endsWith(".pdf")){
                    String subject=classify(name);
                    entries.add(new Entry(uri,name,clean(name),subject,path+name));
                }
            }
        }catch(Exception ignored){/* inaccessible subtree; never change files */}
    }
    private String classify(String name){
        String n=name.toLowerCase(Locale.ROOT);
        int count=0;String result="Needs Review";
        if(n.matches(".*(physics|physic|mechanics|saleem).*")){count++;result="Physics";}
        if(n.matches(".*(chemistry|organic|inorganic).*")){count++;result="Chemistry";}
        if(n.matches(".*(biology|botany|zoology|biohack|cell the unit of life).*")){count++;result="Biology";}
        return count==1?result:"Needs Review";
    }
    private String clean(String name){
        String s=name.replaceAll("(?i)(\\.pdf)+$","")
            .replaceAll("\\[\\s*@[^\\]]+\\]"," ")
            .replaceAll("(?<!\\w)@[\\w.]+"," ")
            .replaceAll("[\\[\\]]"," ")
            .replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]"," ")
            .replaceAll("\\s+"," ").trim();
        if(s.length()>120)s=s.substring(0,120).trim();
        return (s.isEmpty()?"Unnamed":s)+".pdf";
    }
    private void render(String error){
        status.setText("Read-only scan: "+entries.size()+" PDFs. "+(error==null?"No original files changed.":"Partial scan: "+error));
        title("Proposed names (first 100)");
        HashMap<String,Integer> counts=new HashMap<>();
        for(Entry e:entries)counts.put(e.subject,counts.containsKey(e.subject)?counts.get(e.subject)+1:1);
        for(String s:new String[]{"Physics","Chemistry","Biology","Needs Review"}){
            TextView line=new TextView(this);line.setText(s+": "+counts.getOrDefault(s,0));content.addView(line);
        }
        HashSet<String> proposed=new HashSet<>();
        int limit=Math.min(entries.size(),100);
        for(int i=0;i<limit;i++){
            Entry e=entries.get(i);
            String target=e.subject+"/"+e.newName;
            boolean collision=!proposed.add(target.toLowerCase(Locale.ROOT));
            TextView line=new TextView(this);
            line.setPadding(0,12,0,12);
            line.setText(e.relative+"\n  → "+target+(collision?"  [NAME COLLISION]":""));
            content.addView(line);
        }
        TextView footer=new TextView(this);
        footer.setText("Preview only. Rename, move, deletion, cloud upload and ChatGPT API calls are disabled in V1. Some files may need manual review.");
        footer.setPadding(0,20,0,20);content.addView(footer);
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
