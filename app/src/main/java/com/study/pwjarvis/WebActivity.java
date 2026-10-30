package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class WebActivity extends Activity {
    WebView web;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1);
        Button library=new Button(this); library.setText("Offline Library"); root.addView(library);
        library.setOnClickListener(v->startActivity(new Intent(this,LibraryActivity.class)));
        web=new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u=req.getUrl(); String x=u.toString().toLowerCase();
                if(x.endsWith(".pdf") || x.contains(".pdf?")) {
                    Intent i=new Intent(WebActivity.this,PdfActivity.class); i.setData(u); startActivity(i); return true;
                }
                return false;
            }
        });
        setContentView(root);
        web.loadUrl("https://www.pwjarvis.com/study/batches");
    }
    @Override public void onBackPressed(){ if(web!=null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
