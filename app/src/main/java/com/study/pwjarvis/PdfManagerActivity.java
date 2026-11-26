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
    private static final int PICK_TREE=6211, PICK_PDF=6212;
    private final ExecutorService coverIo=Executors.newFixedThreadPool(2);
    private final Map<String,FileInfo> metadata=new HashMap<>();
    private final Map<String,ArrayList<java.lang.ref.WeakReference<ImageView>>> pendingCovers=new HashMap<>();
    private final Set<String> failedCovers=new HashSet<>();
    private static final class FileInfo {
        final String name,parent; final boolean directory; final long size,modified;
        FileInfo(String name,String parent,boolean directory,long size,long modified){
            this.name=name;this.parent=parent;this.directory=directory;this.size=size;this.modified=modified;
        }
    }
    private FileInfo info(DocumentFile f){return metadata.get(f.getUri().toString());}
    private String nameOf(DocumentFile f){FileInfo m=info(f);return m==null?"PDF storage":m.name;}
    private boolean folderOf(DocumentFile f){FileInfo m=info(f);return m!=null&&m.directory;}
    private long sizeOf(DocumentFile f){FileInfo m=info(f);return m==null?0:m.size;}
    private long modifiedOf(DocumentFile f){FileInfo m=info(f);return m==null?0:m.modified;}
    private final ArrayList<DocumentFile> all=new ArrayList<>(), shown=new ArrayList<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Map<String,Bitmap> thumbs=new LinkedHashMap<String,Bitmap>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>48;}
    };
    private GridView grid;
    private BaseAdapter adapter;
    private EditText search;
    private TextView status;
    private DocumentFile tree, currentFolder;
    private final Map<String,String> folderCovers=new HashMap<>();
    private final Set<String> hiddenPaths=new HashSet<>();
    private volatile boolean closed;
    private volatile int scanGeneration;
    private String mode="All PDFs", layout="Book Cover", order="Name A-Z";
    private boolean showHidden=false, showCovers=true, hideRead=false, folderPreview=true;
    private final Set<String> favorites=new HashSet<>(), read=new HashSet<>(), recent=new HashSet<>();
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("pdf_manager",MODE_PRIVATE);}
    private Set<String> stored(String key){return new HashSet<>(prefs().getStringSet(key,Collections.emptySet()));}
    private void save(String key,Set<String> set){prefs().edit().putStringSet(key,new HashSet<>(set)).apply();}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),true);
        getWindow().setStatusBarColor(0xff39489e);getWindow().setNavigationBarColor(0xff39489e);
        favorites.addAll(stored("favorites"));read.addAll(stored("read"));recent.addAll(stored("recent"));
        showHidden=prefs().getBoolean("hidden",false);showCovers=prefs().getBoolean("covers",true);
        hideRead=prefs().getBoolean("hide_read",false);folderPreview=prefs().getBoolean("folder_preview",true);
        layout=prefs().getString("layout","Book Cover");order=prefs().getString("sort","Name A-Z");
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(8),dp(8),dp(8),0);
        root.setBackgroundColor(0xff18191d);
        LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(0xff39489e);
        Button choose=button("Add PDF storage",()->chooseTree());
        bar.addView(choose,new LinearLayout.LayoutParams(0,dp(48),2));
        Button options=button("⊞",()->options());
        bar.addView(button("⇅",this::sortDialog));
        bar.addView(button("▣",()->startActivity(new Intent(this,DeviceDocumentsActivity.class))));
        bar.addView(options,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bar);
        LinearLayout quick=new LinearLayout(this);
        quick.addView(button("Open any PDF",this::pickPdf),new LinearLayout.LayoutParams(0,dp(44),1));
        quick.addView(button("Refresh library",this::scan),new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(quick);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setBackgroundColor(0xff39489e);
        for(String tab:new String[]{"Library","Folder","Recent","Favorites","Bookmarks"}){
            Button t=button(tab,()->{mode=tab;if(tab.equals("Folder"))currentFolder=tree;refresh();});
            t.setTextSize(10);tabs.addView(t,new LinearLayout.LayoutParams(0,dp(45),1));
        }
        
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
                cell.setPadding(dp(5),dp(5),dp(5),dp(5));cell.setBackgroundColor(0xff3e3e42);
                if(showCovers && !layout.equals("Condensed") && (!folderOf(f)||folderPreview)){
                    ImageView image=new ImageView(PdfManagerActivity.this);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setBackgroundColor(0xff424b55);
                    cell.addView(image,new LinearLayout.LayoutParams(-1,dp(layout.equals("Book Cover")?170:layout.equals("Grid")?130:72)));
                    String key=folderOf(f)?folderCovers.get(f.getUri().toString()):f.getUri().toString();Bitmap cached;
                    synchronized(thumbs){cached=thumbs.get(key);}
                    if(cached!=null)image.setImageBitmap(cached);
                    else {image.setImageResource(folderOf(f)?android.R.drawable.ic_menu_agenda:android.R.drawable.ic_menu_gallery);if(key!=null)loadCover(key,image);}
                }
                TextView name=new TextView(PdfManagerActivity.this);
                name.setText(nameOf(f));name.setTextColor(-1);name.setTextSize(layout.equals("Condensed")?13:14);
                name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);cell.addView(name);
                TextView meta=new TextView(PdfManagerActivity.this);meta.setText(folderOf(f)?"Folder":(read.contains(f.getUri().toString())?"Read":"PDF • "+android.text.format.Formatter.formatShortFileSize(PdfManagerActivity.this,sizeOf(f))));
                meta.setTextColor(0xffa9c9d8);LinearLayout actionsRow=new LinearLayout(PdfManagerActivity.this);actionsRow.setGravity(Gravity.CENTER_VERTICAL);
                actionsRow.addView(meta,new LinearLayout.LayoutParams(0,dp(34),1));
                if(!folderOf(f)){TextView heart=new TextView(PdfManagerActivity.this);heart.setText(favorites.contains(f.getUri().toString())?"♥":"♡");heart.setTextSize(22);heart.setTextColor(-1);heart.setGravity(Gravity.CENTER);actionsRow.addView(heart,new LinearLayout.LayoutParams(dp(32),dp(34)));heart.setOnClickListener(v->{String id=f.getUri().toString();if(!favorites.add(id))favorites.remove(id);save("favorites",favorites);refresh();});}
                TextView menu=new TextView(PdfManagerActivity.this);menu.setText("⋮");menu.setTextSize(22);menu.setTextColor(-1);menu.setGravity(Gravity.CENTER);actionsRow.addView(menu,new LinearLayout.LayoutParams(dp(32),dp(34)));menu.setOnClickListener(v->actions(f));cell.addView(actionsRow);
                cell.setOnClickListener(v->open(f));
                final Handler handler=new Handler(Looper.getMainLooper());
                final boolean[] fired={false}, moved={false};
                final float[] origin={0,0};
                Runnable action=()->{if(!closed&&!folderOf(f)){fired[0]=true;cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);rename(f);}};
                cell.setOnTouchListener((v,event)->{
                    switch(event.getActionMasked()){
                        case MotionEvent.ACTION_DOWN: fired[0]=false;moved[0]=false;origin[0]=event.getX();origin[1]=event.getY();handler.postDelayed(action,4000);return true;
                        case MotionEvent.ACTION_MOVE:
                            int slop=ViewConfiguration.get(PdfManagerActivity.this).getScaledTouchSlop();
                            if(Math.abs(event.getX()-origin[0])>slop||Math.abs(event.getY()-origin[1])>slop){moved[0]=true;handler.removeCallbacks(action);}return true;
                        case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:moved[0]=true;handler.removeCallbacks(action);return true;
                        case MotionEvent.ACTION_UP:handler.removeCallbacks(action);if(!fired[0]&&!moved[0])v.performClick();return true;
                    }return true;
                });
                cell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){handler.removeCallbacks(action);}});
                return cell;
            }
        };
        grid.setAdapter(adapter);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(tabs);
        setContentView(root);setLayout();
        String saved=prefs().getString("tree",null);
        if(saved!=null){try{tree=DocumentFile.fromTreeUri(this,Uri.parse(saved));}catch(Exception ignored){}}
        if(tree!=null)scan();else status.setText("Choose a storage folder to show all PDFs inside it and subfolders.");
    }
    private Button button(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->run.run());return b;}
    private void pickPdf(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_PDF);}catch(Exception e){Toast.makeText(this,"PDF picker unavailable",Toast.LENGTH_LONG).show();}
    }
    private void chooseTree(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_TREE);}catch(Exception e){Toast.makeText(this,"Storage picker unavailable",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK_PDF){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            try{startActivity(new Intent(this,PdfActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){Toast.makeText(this,"Unable to open PDF",Toast.LENGTH_LONG).show();}
            return;
        }
        if(request!=PICK_TREE)return;
        Uri uri=data.getData();
        try{
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri,flags);
            tree=DocumentFile.fromTreeUri(this,uri);currentFolder=tree;prefs().edit().putString("tree",uri.toString()).apply();scan();
        }catch(Exception e){Toast.makeText(this,"Storage access failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void scan(){
        if(closed)return;
        if(tree==null){status.setText("Add a storage folder first.");return;}
        final int generation=++scanGeneration;
        status.setText("Scanning PDFs…");
        DocumentFile root=tree;
        io.execute(()->{
            ArrayList<DocumentFile> found=new ArrayList<>();
            ArrayDeque<DocumentFile> queue=new ArrayDeque<>();queue.add(root);
            HashSet<String> visited=new HashSet<>(), hidden=new HashSet<>();
            HashMap<String,String> previews=new HashMap<>(), parents=new HashMap<>();
            HashMap<String,FileInfo> discovered=new HashMap<>();
            int errors=0;
            try{discovered.put(root.getUri().toString(),new FileInfo(root.getName()==null?"PDF storage":root.getName(),null,true,0,0));if(!root.canRead())errors++;}catch(Exception e){errors++;}
            while(!queue.isEmpty()&&visited.size()<20000&&!closed&&generation==scanGeneration){
                DocumentFile dir=queue.removeFirst();
                if(!visited.add(dir.getUri().toString()))continue;
                try{for(DocumentFile child:dir.listFiles()){
                    String name=child.getName();
                    if(name==null)continue;
                    String id=child.getUri().toString(),parent=dir.getUri().toString();
                    if(name.startsWith(".")||hidden.contains(parent))hidden.add(id);
                    boolean directory=child.isDirectory();
                    if(directory||name.toLowerCase(Locale.ROOT).endsWith(".pdf"))discovered.put(id,new FileInfo(name,parent,directory,directory?0:child.length(),child.lastModified()));
                    if(directory){queue.add(child);found.add(child);parents.put(id,parent);}
                    else if(name.toLowerCase(Locale.ROOT).endsWith(".pdf")){
                        found.add(child);
                        if(!hidden.contains(id)){String ancestor=parent;while(ancestor!=null&&!previews.containsKey(ancestor)){previews.put(ancestor,id);ancestor=parents.get(ancestor);}}
                    }
                }}catch(Exception ignored){errors++;}
            }
            final int failed=errors;final boolean limited=!queue.isEmpty();
            runOnUiThread(()->{if(closed||generation!=scanGeneration)return;all.clear();all.addAll(found);metadata.clear();metadata.putAll(discovered);folderCovers.clear();folderCovers.putAll(previews);hiddenPaths.clear();hiddenPaths.addAll(hidden);failedCovers.clear();synchronized(thumbs){thumbs.clear();}refresh();if(failed>0||limited)status.append(" • Partial scan; check folder access or scan a smaller folder");});
        });
    }
    private void refresh(){
        if(grid==null)return;
        shown.clear();String q=search.getText().toString().toLowerCase(Locale.ROOT);
        for(DocumentFile f:all){
            String id=f.getUri().toString(),name=nameOf(f);
            if(!showHidden&&(name.startsWith(".")||hiddenPaths.contains(id)))continue;
            if(mode.equals("Folder")){
                FileInfo m=info(f);
                DocumentFile wanted=currentFolder==null?tree:currentFolder;
                if(m==null||wanted==null||!wanted.getUri().toString().equals(m.parent))continue;
            }else if(folderOf(f))continue;
            if(hideRead&&read.contains(id))continue;
            if(!name.toLowerCase(Locale.ROOT).contains(q))continue;
            if(mode.equals("Favorites")&&!favorites.contains(id))continue;
            if(mode.equals("Recent")&&!recent.contains(id)&&prefs().getLong("recent:"+id,0)==0)continue;
            if(mode.equals("Bookmarks")&&PdfStudyStorage.bookmarks(this,id).isEmpty())continue;
            shown.add(f);
        }
        Collections.sort(shown,(a,b)->{
            if(folderOf(a)!=folderOf(b))return folderOf(a)?-1:1;
            if(mode.equals("Recent"))return Long.compare(prefs().getLong("recent:"+b.getUri(),0),prefs().getLong("recent:"+a.getUri(),0));
            if(order.equals("Newest"))return Long.compare(modifiedOf(b),modifiedOf(a));
            if(order.equals("Oldest"))return Long.compare(modifiedOf(a),modifiedOf(b));
            if(order.equals("Largest"))return Long.compare(sizeOf(b),sizeOf(a));
            String an=nameOf(a),bn=nameOf(b);
            return order.equals("Name Z-A")?bn.compareToIgnoreCase(an):an.compareToIgnoreCase(bn);
        });
        status.setText((mode.equals("Folder")&&currentFolder!=null?nameOf(currentFolder):mode)+" • "+shown.size()+" items • "+layout);
        adapter.notifyDataSetChanged();
    }
    private void setLayout(){grid.setNumColumns(layout.equals("Book Cover")?3:layout.equals("Grid")?2:1);}
    private void options(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(18),dp(8),dp(18),dp(8));
        String[] names={"Folder preview","Show hidden","Show covers for added storage","Hide read books"};
        String[] keys={"folder_preview","hidden","covers","hide_read"};
        boolean[] values={folderPreview,showHidden,showCovers,hideRead};
        for(int i=0;i<names.length;i++){final int index=i;CheckBox c=new CheckBox(this);c.setText(names[i]);c.setChecked(values[i]);panel.addView(c);c.setOnCheckedChangeListener((v,on)->{prefs().edit().putBoolean(keys[index],on).apply();if(index==0)folderPreview=on;if(index==1)showHidden=on;if(index==2)showCovers=on;if(index==3)hideRead=on;refresh();});}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("PDF display options").setView(panel).setNegativeButton("Close",null).create();
        for(String name:new String[]{"List","Condensed","Grid","Book Cover"})panel.addView(button((name.equals(layout)?"✓  ":"")+name,()->{layout=name;prefs().edit().putString("layout",layout).apply();setLayout();refresh();dialog.dismiss();}));
        panel.addView(button("Rescan",()->{dialog.dismiss();scan();}));dialog.show();
    }
    private void sortDialog(){
        String[] choices={"Name A-Z","Name Z-A","Newest","Oldest","Largest"};
        new AlertDialog.Builder(this).setTitle("Sort PDFs").setItems(choices,(d,w)->{
            order=choices[w];prefs().edit().putString("sort",order).apply();refresh();
        }).show();
    }
    private void sharePdf(DocumentFile f){
        Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,f.getUri());
        send.setClipData(ClipData.newRawUri("PDF",f.getUri()));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(send,"Share PDF"));}catch(Exception e){Toast.makeText(this,"Sharing unavailable",Toast.LENGTH_SHORT).show();}
    }
    private void actions(DocumentFile f){
        if(folderOf(f)){open(f);return;}
        String id=f.getUri().toString();
        new AlertDialog.Builder(this).setTitle(nameOf(f)).setItems(new String[]{"Preview / Open","Rename PDF",
            favorites.contains(id)?"Remove favorite":"Add favorite",read.contains(id)?"Mark unread":"Mark read",
            "File information","Share PDF"},(d,w)->{
            if(w==0)open(f);if(w==1)rename(f);
            if(w==2){if(!favorites.add(id))favorites.remove(id);save("favorites",favorites);refresh();}
            if(w==3){if(!read.add(id))read.remove(id);save("read",read);refresh();}
            if(w==4)new AlertDialog.Builder(this).setMessage(nameOf(f)+"\n"+android.text.format.Formatter.formatFileSize(this,sizeOf(f))+"\n"+id).setPositiveButton("OK",null).show();
            if(w==5)sharePdf(f);
        }).show();
    }
    private void rename(DocumentFile f){
        EditText input=new EditText(this);String old=nameOf(f);final String previousId=f.getUri().toString();
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
                    runOnUiThread(()->{if(closed)return;Toast.makeText(this,success?"Renamed":"Rename denied by storage provider",Toast.LENGTH_LONG).show();if(success){String newId=f.getUri().toString();if(!previousId.equals(newId)){migrateSet(favorites,"favorites",previousId,newId);migrateSet(read,"read",previousId,newId);migrateSet(recent,"recent",previousId,newId);long last=prefs().getLong("recent:"+previousId,0);prefs().edit().remove("recent:"+previousId).putLong("recent:"+newId,last).apply();}scan();}});
                });
            }).show();
    }
    private void migrateSet(Set<String> set,String key,String oldId,String newId){if(set.remove(oldId)){set.add(newId);save(key,set);}}
    private void open(DocumentFile f){
        if(folderOf(f)){currentFolder=f;mode="Folder";search.setText("");refresh();return;}
        if(mode.equals("Bookmarks")){List<Integer> pages=PdfStudyStorage.bookmarks(this,f.getUri().toString());if(!pages.isEmpty()){String[] labels=new String[pages.size()];for(int j=0;j<pages.size();j++)labels[j]="Page "+(pages.get(j)+1);new AlertDialog.Builder(this).setTitle(nameOf(f)).setItems(labels,(d,n)->launch(f,pages.get(n))).show();return;}}
        launch(f,-1);
    }
    private void launch(DocumentFile f,int page){
        String id=f.getUri().toString();recent.add(id);save("recent",recent);prefs().edit().putLong("recent:"+id,System.currentTimeMillis()).apply();
        Intent i=new Intent(this,PdfActivity.class);i.setData(f.getUri());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(page>=0)i.putExtra("initial_page",page);
        try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Unable to open PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void loadCover(String key,ImageView view){
        if(closed||failedCovers.contains(key))return;
        view.setTag(key);
        ArrayList<java.lang.ref.WeakReference<ImageView>> waiting=pendingCovers.get(key);
        if(waiting!=null){waiting.add(new java.lang.ref.WeakReference<>(view));return;}
        waiting=new ArrayList<>();waiting.add(new java.lang.ref.WeakReference<>(view));pendingCovers.put(key,waiting);
        coverIo.execute(()->{
            Bitmap bitmap=null;
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(key),"r")){
                if(fd!=null)try(PdfRenderer renderer=new PdfRenderer(fd)){
                    if(renderer.getPageCount()>0)try(PdfRenderer.Page page=renderer.openPage(0)){
                        float scale=Math.min(240f/page.getWidth(),360f/page.getHeight());int width=Math.max(1,Math.round(page.getWidth()*scale)),height=Math.max(1,Math.round(page.getHeight()*scale));
                        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }
            }catch(Exception ignored){}
            final Bitmap ready=bitmap;
            runOnUiThread(()->{ArrayList<java.lang.ref.WeakReference<ImageView>> targets=pendingCovers.remove(key);if(closed)return;if(ready==null){failedCovers.add(key);return;}synchronized(thumbs){thumbs.put(key,ready);}if(targets!=null)for(java.lang.ref.WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null&&key.equals(target.getTag()))target.setImageBitmap(ready);}});
        });
    }
    @Override protected void onResume(){super.onResume();if(grid!=null)refresh();}
    @Override public void onBackPressed(){if(mode.equals("Folder")&&currentFolder!=null&&tree!=null&&!currentFolder.getUri().equals(tree.getUri())){currentFolder=currentFolder.getParentFile();refresh();}else if(!mode.equals("Library")&&!mode.equals("All PDFs")){mode="Library";refresh();}else super.onBackPressed();}
    @Override protected void onDestroy(){closed=true;scanGeneration++;io.shutdownNow();coverIo.shutdownNow();pendingCovers.clear();super.onDestroy();}
}
