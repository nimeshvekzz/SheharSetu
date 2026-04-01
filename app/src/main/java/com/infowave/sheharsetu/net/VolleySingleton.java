package com.infowave.sheharsetu.net;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

public class VolleySingleton {
    private static final String TAG = "VolleySingleton";
    private static volatile VolleySingleton instance;

    private final RequestQueue queue;

    private VolleySingleton(Context ctx) {
        Context appCtx = ctx.getApplicationContext();

        boolean isDebuggable =
                (appCtx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        if (isDebuggable) {
            queue = Volley.newRequestQueue(
                    appCtx,
                    new HostOnlyUnsafeHurlStack("magenta-owl-444153.hostingersite.com")
            );
        } else {
            queue = Volley.newRequestQueue(appCtx, new HurlStack());
        }
    }

    public static VolleySingleton getInstance(Context ctx) {
        if (instance == null) {
            synchronized (VolleySingleton.class) {
                if (instance == null) instance = new VolleySingleton(ctx);
            }
        }
        return instance;
    }

    /** ✅ Use this in your Activities: RequestQueue q = VolleySingleton.queue(this); */
    public static RequestQueue queue(Context ctx) {
        return getInstance(ctx).getQueue();
    }

    public RequestQueue getQueue() {
        return queue;
    }

    public <T> void add(Request<T> req) {
        queue.add(req);
    }
}
