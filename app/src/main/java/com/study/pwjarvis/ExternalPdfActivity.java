package com.study.pwjarvis;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
public class ExternalPdfActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Uri uri=getIntent().getData();
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())&&uri!=null&&"content".equalsIgnoreCase(uri.getScheme())){
            try{startActivity(new Intent(this,PdfActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){Toast.makeText(this,"Unable to open PDF",Toast.LENGTH_LONG).show();}
        }else Toast.makeText(this,"Invalid PDF document",Toast.LENGTH_LONG).show();
        finish();
    }
}
