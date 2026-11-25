package com.study.pwjarvis;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import java.util.*;
import java.util.concurrent.*;

/** Additive device-PDF browser. Never changes the existing home or video player. */
public class PdfManagerActivity extends Activity {
    private static final int PICK_TREE=6211;
    private final ArrayList<DocumentFile> all=new ArrayList<>(), shown=new ArrayList<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Map<String,Bitmap> thumbs=new LinkedHashMap<String,Bitmap>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>48;}
    };
    private GridView grid;
    private BaseAdapter adapter;
    private EditText search;
    private TextView status;
    private DocumentFile tree;
    private String mode="All PDFs", layout="Grid", order="Name A-Z";
    private boolean showHidden=false, showCovers=true, hideRead=false, folderPreview=true;
    private final Set<String> favorites=new HashSet<>(), read=new HashSet<>(), recent=new HashSet<>();
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("pdf_manager",MODE_PRIVATE);}
    private Set<String> stored(String key){return new HashSet<>(prefs().getStringSet(key,Collections.emptySet()));}
    private void save(String key,Set<String> set){prefs().edit().putStringSet(key,new HashSet<>(set)).apply();}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        favorites.addAll(stored("favorites"));read.addAll(stored("read"));recent.addAll(stored("recent"));
        showHidden=prefs().getBoolean("hidden",false);showCovers=prefs().getBoolean("covers",true);
        hideRead=prefs().getBoolean("hide_read",false);folderPreview=prefs().getBoolean("folder_preview",true);
        layout=prefs().getString("layout","Grid");order=prefs().getString("sort","Name A-Z");
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(8),dp(8),dp(8),0);
        root.setBackgroundColor(0xff101722);
        LinearLayout bar=new LinearLayout(this);
        Button choose=button("Add PDF storage",()->chooseTree());
        bar.addView(choose,new LinearLayout.LayoutParams(0,dp(48),2));
        Button options=button("⋮ Options",()->options());
        bar.addView(options,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bar);
        LinearLayout tabs=new LinearLayout(this);
        for(String tab:new String[]{"All PDFs","Favorites","Recent","Bookmarks"}){
            Button t=button(tab,()->{mode=tab;refresh();});
            t.setTextSize(10);tabs.addView(t,new LinearLayout.LayoutParams(0,dp(45),1));
        }
        root.addView(tabs);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Search all PDFs");
        search.setTextColor(-1);search.setHintTextColor(0xffaaaaaa);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){refresh();}
            public void afterTextChanged(Editable e){}
        });
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        status=new TextView(this);status.setTextColor(-1);status.setPadding(dp(8),dp(8),0,dp(8));root.addView(status);
        grid=new GridView(this);grid.setVerticalSpacing(dp(8));grid.setHorizontalSpacing(dp(6));
        adapter=new BaseAdapter(){
            public int getCount(){return shown.size();}
            public Object getItem(int p){return shown.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){
                DocumentFile f=shown.get(p);
                LinearLayout cell=new LinearLayout(PdfManagerActivity.this);cell.setOrientation(1);
                cell.setPadding(dp(5),dp(5),dp(5),dp(5));cell.setBackgroundColor(0xff27313d);
                if(showCovers && !layout.equals("Condensed")){
                    ImageView image=new ImageView(PdfManagerActivity.this);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setBackgroundColor(0xff424b55);
                    cell.addView(image,new LinearLayout.LayoutParams(-1,dp(layout.equals("Book Cover")?170:layout.equals("Grid")?130:72)));
                    String key=f.getUri().toString();Bitmap cached;
                    synchronized(thumbs){cached=thumbs.get(key);}
                    if(cached!=null)image.setImageBitmap(cached);
                    else {image.setImageResource(android.R.drawable.ic_menu_gallery);loadCover(key,image);}
                }
                TextView name=new TextView(PdfManagerActivity.this);
                name.setText(f.getName());name.setTextColor(-1);name.setTextSize(layout.equals("Condensed")?13:14);
                name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);cell.addView(name);
                TextView meta=new TextView(PdfManagerActivity.this);meta.setText((favorites.contains(f.getUri().toString())?"♥  ":"")+"PDF");
                meta.setTextColor(0xffa9c9d8);cell.addView(meta);
                cell.setOnClickListener(v->open(f));
                // Exactly four seconds, not Android's default ~500ms long click.
                Handler handler=new Handler(Looper.getMainLooper());
                final boolean[] fired={false};
                Runnable action=()->{fired[0]=true;actions(f);};
                cell.setOnTouchListener((v,event)->{
                    if(event.getAction()==MotionEvent.ACTION_DOWN){fired[0]=false;handler.postDelayed(action,4000);return true;}
                    if(event.getAction()==MotionEvent.ACTION_UP){handler.removeCallbacks(action);if(!fired[0])open(f);return true;}
                    if(event.getAction()==MotionEvent.ACTION_CANCEL || event.getAction()==MotionEvent.ACTION_MOVE &&
                       (event.getX()<0||event.getY()<0||event.getX()>v.getWidth()||event.getY()>v.getHeight())){
                        handler.removeCallbacks(action);return true;
                    }
                    return true;
                });
                return cell;
            }
        };
        grid.setAdapter(adapter);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);setLayout();
        String saved=prefs().getString("tree",null);
        if(saved!=null){try{tree=DocumentFile.fromTreeUri(this,Uri.parse(saved));}catch(Exception ignored){}}
        if(tree!=null&&tree.canRead())scan();else status.setText("Choose a storage folder to show all PDFs inside it and subfolders.");
    }
    private Button button(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->run.run());return b;}
    private void chooseTree(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_TREE||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri,flags);
            tree=DocumentFile.fromTreeUri(this,uri);prefs().edit().putString("tree",uri.toString()).apply();scan();
        }catch(Exception e){Toast.makeText(this,"Storage access failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void scan(){
        if(tree==null)return;
        status.setText("Scanning PDFs…");
        DocumentFile root=tree;
        io.execute(()->{
            ArrayList<DocumentFile> found=new ArrayList<>();
            ArrayDeque<DocumentFile> queue=new ArrayDeque<>();queue.add(root);
            HashSet<String> visited=new HashSet<>();
            while(!queue.isEmpty()&&visited.size()<20000){
                DocumentFile dir=queue.removeFirst();
                if(!visited.add(dir.getUri().toString()))continue;
                try{for(DocumentFile child:dir.listFiles()){
                    String name=child.getName();
                    if(name==null)continue;
                    if(child.isDirectory())queue.add(child);
                    else if(name.toLowerCase(Locale.ROOT).endsWith(".pdf"))found.add(child);
                }}catch(Exception ignored){}
            }
            runOnUiThread(()->{all.clear();all.addAll(found);refresh();});
        });
    }
    private void refresh(){
        if(grid==null)return;
        shown.clear();String q=search.getText().toString().toLowerCase(Locale.ROOT);
        for(DocumentFile f:all){
            String id=f.getUri().toString(),name=f.getName()==null?"":f.getName();
            if(!showHidden&&name.startsWith("."))continue;
            if(hideRead&&read.contains(id))continue;
            if(!name.toLowerCase(Locale.ROOT).contains(q))continue;
            if(mode.equals("Favorites")&&!favorites.contains(id))continue;
            if(mode.equals("Recent")&&!recent.contains(id))continue;
            if(mode.equals("Bookmarks")&&!favorites.contains(id))continue;
            shown.add(f);
        }
        Collections.sort(shown,(a,b)->{
            if(order.equals("Newest"))return Long.compare(b.lastModified(),a.lastModified());
            if(order.equals("Oldest"))return Long.compare(a.lastModified(),b.lastModified());
            if(order.equals("Largest"))return Long.compare(b.length(),a.length());
            String an=a.getName()==null?"":a.getName(),bn=b.getName()==null?"":b.getName();
            return order.equals("Name Z-A")?bn.compareToIgnoreCase(an):an.compareToIgnoreCase(bn);
        });
        status.setText(mode+" • "+shown.size()+" PDFs • "+layout);
        adapter.notifyDataSetChanged();
    }
    private void setLayout(){grid.setNumColumns(layout.equals("Book Cover")?3:layout.equals("Grid")?2:1);}
    private void options(){
        String[] choices={"Folder preview: "+folderPreview,"Show hidden: "+showHidden,"Show covers: "+showCovers,
            "Hide read PDFs: "+hideRead,"List","Condensed","Grid","Book Cover","Sort PDFs","Rescan"};
        new AlertDialog.Builder(this).setTitle("PDF display options").setItems(choices,(d,w)->{
            if(w==0){folderPreview=!folderPreview;prefs().edit().putBoolean("folder_preview",folderPreview).apply();}
            if(w==1){showHidden=!showHidden;prefs().edit().putBoolean("hidden",showHidden).apply();}
            if(w==2){showCovers=!showCovers;prefs().edit().putBoolean("covers",showCovers).apply();}
            if(w==3){hideRead=!hideRead;prefs().edit().putBoolean("hide_read",hideRead).apply();}
            if(w>=4&&w<=7){layout=new String[]{"List","Condensed","Grid","Book Cover"}[w-4];prefs().edit().putString("layout",layout).apply();setLayout();}
            if(w==8)sortDialog();else if(w==9)scan();else refresh();
        }).show();
    }
    private void sortDialog(){
        String[] choices={"Name A-Z","Name Z-A","Newest","Oldest","Largest"};
        new AlertDialog.Builder(this).setTitle("Sort PDFs").setItems(choices,(d,w)->{
            order=choices[w];prefs().edit().putString("sort",order).apply();refresh();
        }).show();
    }
    private void actions(DocumentFile f){
        String id=f.getUri().toString();
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{"Preview / Open","Rename PDF",
            favorites.contains(id)?"Remove favorite":"Add favorite",read.contains(id)?"Mark unread":"Mark read",
            "File information"},(d,w)->{
            if(w==0)open(f);if(w==1)rename(f);
            if(w==2){if(!favorites.add(id))favorites.remove(id);save("favorites",favorites);refresh();}
            if(w==3){if(!read.add(id))read.remove(id);save("read",read);refresh();}
            if(w==4)new AlertDialog.Builder(this).setMessage(f.getName()+"\n"+f.length()+" bytes\n"+id).setPositiveButton("OK",null).show();
        }).show();
    }
    private void rename(DocumentFile f){
        EditText input=new EditText(this);String old=f.getName()==null?"":f.getName();
        input.setText(old.toLowerCase(Locale.ROOT).endsWith(".pdf")?old.substring(0,old.length()-4):old);
        new AlertDialog.Builder(this).setTitle("Rename PDF").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Rename",(d,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")){
                    Toast.makeText(this,"Invalid file name",Toast.LENGTH_LONG).show();return;
                }
                String newName=name.toLowerCase(Locale.ROOT).endsWith(".pdf")?name:name+".pdf";
                io.execute(()->{
                    boolean ok=false;try{ok=f.renameTo(newName);}catch(Exception ignored){}
                    final boolean success=ok;
                    runOnUiThread(()->{Toast.makeText(this,success?"Renamed":"Rename denied by storage provider",Toast.LENGTH_LONG).show();if(success)scan();});
                });
            }).show();
    }
    private void open(DocumentFile f){
        String id=f.getUri().toString();recent.add(id);save("recent",recent);
        Intent i=new Intent(this,PdfActivity.class);i.setData(f.getUri());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Unable to open PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void loadCover(String key,ImageView view){
        io.execute(()->{
            Bitmap bitmap=null;
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(key),"r")){
                if(fd!=null)try(PdfRenderer renderer=new PdfRenderer(fd)){
                    if(renderer.getPageCount()>0)try(PdfRenderer.Page page=renderer.openPage(0)){
                        int width=180,height=Math.max(1,(int)(180f*page.getHeight()/page.getWidth()));
                        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
                        bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }
            }catch(Exception ignored){}
            if(bitmap!=null){Bitmap ready=bitmap;synchronized(thumbs){thumbs.put(key,ready);}
                runOnUiThread(()->{if(view.isAttachedToWindow())view.setImageBitmap(ready);});
            }
        });
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
