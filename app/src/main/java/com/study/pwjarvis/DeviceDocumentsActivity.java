package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DeviceDocumentsActivity extends Activity {
    private static final int PICK_FOLDER=4102;
    private DocumentFile rootFolder,currentFolder;
    private final List<DocumentFile> visible=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView list;
    private TextView location;
    private EditText search;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.setBackgroundColor(Color.rgb(12,16,25));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("DEVICE DOCUMENTS"); title.setTextColor(Color.WHITE);
        title.setTextSize(19); title.setTypeface(null,1);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button customize=new Button(this); customize.setText("Style"); customize.setAllCaps(false);\n        customize.setOnClickListener(v->startActivity(new Intent(this,CustomizationActivity.class)));\n        top.addView(customize,new LinearLayout.LayoutParams(-2,dp(44)));\n        Button choose=new Button(this); choose.setText("Choose folder"); choose.setAllCaps(false);
        top.addView(choose,new LinearLayout.LayoutParams(-2,dp(46)));
        choose.setOnClickListener(v->pickFolder());
        root.addView(top);

        location=new TextView(this); location.setTextColor(Color.LTGRAY);
        location.setPadding(dp(4),dp(8),dp(4),dp(8)); root.addView(location);

        search=new EditText(this); search.setSingleLine(true);
        search.setHint("Search in this folder"); search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){refresh();}
            public void afterTextChanged(android.text.Editable e){}
        });
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout nav=new LinearLayout(this);
        Button up=new Button(this); up.setText("↑ Up"); up.setAllCaps(false);
        up.setOnClickListener(v->goUp());
        Button refresh=new Button(this); refresh.setText("Refresh"); refresh.setAllCaps(false);
        refresh.setOnClickListener(v->refresh());
        nav.addView(up,new LinearLayout.LayoutParams(0,dp(44),1));
        nav.addView(refresh,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(nav);

        list=new ListView(this);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){\n            @Override public android.view.View getView(int position,android.view.View convertView,android.view.ViewGroup parent){\n                android.view.View row=super.getView(position,convertView,parent);\n                ((TextView)row.findViewById(android.R.id.text1)).setTextColor(AppAppearance.textColor(DeviceDocumentsActivity.this));\n                row.setBackgroundColor(Color.TRANSPARENT); return row;\n            }\n        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{
            if(pos<0||pos>=visible.size())return;
            DocumentFile item=visible.get(pos);
            if(item.isDirectory()){currentFolder=item;refresh();} else openDocument(item);
        });
        setContentView(root);
        String saved=getPreferences(MODE_PRIVATE).getString("tree",null);
        if(saved!=null){rootFolder=DocumentFile.fromTreeUri(this,Uri.parse(saved));currentFolder=rootFolder;}
        if(rootFolder==null||!rootFolder.canRead())pickFolder(); else refresh();
    }

    @Override protected void onResume(){\n        super.onResume();\n        android.view.ViewGroup content=findViewById(android.R.id.content);\n        if(content.getChildCount()>0)AppAppearance.apply(this,content.getChildAt(0));\n    }\n\n    private void pickFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_FOLDER);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_FOLDER&&result==RESULT_OK&&data!=null){
            Uri uri=data.getData();
            try{
                int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri,flags);
                getPreferences(MODE_PRIVATE).edit().putString("tree",uri.toString()).apply();
                rootFolder=DocumentFile.fromTreeUri(this,uri); currentFolder=rootFolder; refresh();
            }catch(Exception e){Toast.makeText(this,"Folder access failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }

    private void goUp(){
        if(currentFolder==null||rootFolder==null||currentFolder.getUri().equals(rootFolder.getUri()))return;
        DocumentFile parent=currentFolder.getParentFile();
        if(parent!=null){currentFolder=parent;refresh();}
    }

    private void refresh(){
        if(list==null||currentFolder==null)return;
        visible.clear(); labels.clear();
        String q=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        DocumentFile[] children=currentFolder.listFiles();
        java.util.Arrays.sort(children,(a,b)->{
            if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;
            String an=a.getName()==null?"":a.getName(),bn=b.getName()==null?"":b.getName();
            return an.compareToIgnoreCase(bn);
        });
        for(DocumentFile f:children){
            String name=f.getName()==null?"Unnamed":f.getName();
            if(!q.isEmpty()&&!name.toLowerCase(java.util.Locale.ROOT).contains(q))continue;
            visible.add(f);
            labels.add((f.isDirectory()?"📁  ":"📄  ")+name+(f.isDirectory()?"/":"   •   "+size(f.length())));
        }
        adapter.notifyDataSetChanged();
        location.setText("Folder: "+(currentFolder.getName()==null?"Selected device folder":currentFolder.getName())+"   •   "+visible.size()+" items");
    }

    private String size(long n){
        if(n<1024)return n+" B";
        if(n<1024*1024)return String.format(java.util.Locale.US,"%.1f KB",n/1024d);
        return String.format(java.util.Locale.US,"%.1f MB",n/(1024d*1024d));
    }

    private void openDocument(DocumentFile doc){
        String name=doc.getName()==null?"document":doc.getName();
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        try{
            if(lower.endsWith(".pdf")||lower.endsWith(".mp4")||lower.endsWith(".mkv")||lower.endsWith(".webm")||lower.endsWith(".mov")){
                File cache=new File(getCacheDir(),"device_open"); if(!cache.exists())cache.mkdirs();
                File target=new File(cache,name.replaceAll("[^a-zA-Z0-9._-]","_"));
                try(InputStream in=getContentResolver().openInputStream(doc.getUri());FileOutputStream out=new FileOutputStream(target)){
                    if(in==null)throw new Exception("Cannot read selected file");
                    byte[] buffer=new byte[16384]; int count;
                    while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                }
                Intent intent=lower.endsWith(".pdf")?new Intent(this,PdfActivity.class):new Intent(this,PlayerActivity.class);
                intent.putExtra("file_path",target.getAbsolutePath()); startActivity(intent);
            }else{
                Intent view=new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(doc.getUri(),getContentResolver().getType(doc.getUri()));
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(view,"Open document"));
            }
        }catch(Exception e){Toast.makeText(this,"Cannot open file: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override public void onBackPressed(){
        if(currentFolder!=null&&rootFolder!=null&&!currentFolder.getUri().equals(rootFolder.getUri())){goUp();return;}
        super.onBackPressed();
    }
}