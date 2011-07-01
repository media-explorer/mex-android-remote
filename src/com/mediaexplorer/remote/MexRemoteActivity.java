/*
 * Mex android remote - a wraper and discovery tool for the mex web remote
 *
 * Copyright © 2011 Intel Corporation.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms and conditions of the GNU Lesser General Public License,
 * version 2.1, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>
 */

package com.mediaexplorer.remote;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.widget.TextView;
import android.app.ProgressDialog;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.HttpAuthHandler;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.method.PasswordTransformationMethod;
import android.view.ViewGroup.LayoutParams;


public class MexRemoteActivity extends Activity
{
    /* Called when the activity is first created. */

    private android.net.wifi.WifiManager.MulticastLock lock;
    private android.os.Handler handler = new android.os.Handler();
    private JmDNS jmdns = null;
    private ServiceListener listener = null;
    private ProgressDialog dialog;
    private TextView text_view;
    private WebView web_view;

    @Override
    public void onCreate(Bundle savedInstanceState)
      {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        this.setTitle (R.string.app_name);

        text_view = (TextView)findViewById (R.id.text);

        web_view = (WebView)findViewById (R.id.link_view);
        web_view.getSettings().setJavaScriptEnabled(true);

        web_view.setWebViewClient(new WebViewClient()
             {
                /* The mex remote can have authentication */
                @Override
                public void
                onReceivedHttpAuthRequest (WebView         view,
                                           final HttpAuthHandler handler,
                                           String          host,
                                           String          realm)
                {
                  String[] userpass =
                    view.getHttpAuthUsernamePassword(host, realm);

                  if (userpass != null && userpass.length == 2)
                    {
                      handler.proceed(userpass[0], userpass[1]);
                    }
                  else
                    {
                      /* login dialog box */
                      final AlertDialog.Builder logindialog;
                      final EditText user;
                      final EditText pass;

                      LinearLayout layout;
                      LayoutParams params;

                      TextView label_username;
                      TextView label_password;

                      logindialog =
                              new AlertDialog.Builder(MexRemoteActivity.this);

                      logindialog.setTitle("Mex Webremote login");

                      user = new EditText (MexRemoteActivity.this);
                      pass = new EditText (MexRemoteActivity.this);

                      layout = new LinearLayout (MexRemoteActivity.this);

                      pass.setTransformationMethod (new PasswordTransformationMethod());

                      layout.setOrientation(LinearLayout.VERTICAL);

                      params = new LayoutParams (LayoutParams.FILL_PARENT,
                                                 LayoutParams.FILL_PARENT);

                      layout.setLayoutParams(params);
                      user.setLayoutParams(params);
                      pass.setLayoutParams(params);

                      label_username = new TextView (MexRemoteActivity.this);
                      label_password = new TextView (MexRemoteActivity.this);

                      label_username.setText("Username:");
                      label_password.setText("Password:");

                      layout.addView(label_username);
                      layout.addView(user);
                      layout.addView(label_password);
                      layout.addView(pass);
                      logindialog.setView(layout);

                      logindialog.setPositiveButton("Login",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int whichButton) {
                                                String uvalue = user.getText()
                                                        .toString().trim();
                                                String pvalue = pass.getText()
                                                        .toString().trim();
                                                handler.proceed(uvalue, pvalue);
                                            }
                                        });

                      logindialog.setNegativeButton("Cancel",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int whichButton) {
                                                dialog.cancel();
                                            }
                                        });
                      logindialog.show();
                    } /* End login dialog box */
                 }
              });

        /* run mdns to check for services don't block the ui*/
        handler.post (new Runnable ()
                      {
                      public void run()
                        {
                          startMdns ();
                        }
                      });

        dialog = ProgressDialog.show(MexRemoteActivity.this, "",
                                     "Searching...", true);

        /* Let's put something in the webview while we're waiting */
        String summary = "<html><head><style>body { background-color: #000000; color: #ffffff; }></style></head><body><p>Searching for the media explorer webservice</p><p>More infomation on <a href=\"http://media-explorer.github.com\" >Media Explorer's home page</a></p></body></html>";
        web_view.loadData(summary, "text/html", "utf-8");

      } /* end OnCreate */

    private void startMdns ()
      {
        android.net.wifi.WifiManager wifi;

        wifi = (android.net.wifi.WifiManager) getSystemService (android.content.Context.WIFI_SERVICE);

        /* We need to specifically turn get multicast packets as usually
         * they're disgarded, turn off soon as possible due to battery drain
         */

        lock = wifi.createMulticastLock ("mexremote");
        lock.setReferenceCounted (true);
        lock.acquire();

        try
          {
            jmdns = JmDNS.create ();
            jmdns.addServiceListener ("_http._tcp.local.", listener = new ServiceListener () {
            @Override
            public void serviceResolved (ServiceEvent event)
             {
               if (event.getName().startsWith ("Mex Webremote"))
                  {
                    dialog.dismiss();

                    /* TODO handle multiple mex web remotes and give an option
                     *  to select a particular one
                     */
                    remoteFound ("http:/" + event.getInfo().getInetAddresses()[0] + ":" + event.getInfo().getPort());

                  /* release asap to save battery */
                  lock.release ();
                  }
             }

            @Override
            public void serviceRemoved(ServiceEvent event)
             {
              dialog.show ();
              jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
             }

             @Override
             public void serviceAdded(ServiceEvent event)
              {
               /* Ask for the new item to be resolved */
               jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
              }

         });
        }
        catch (IOException exception)
          {
            exception.printStackTrace ();
            return;
          }
      }

    private void remoteFound (final String response)
      {
        handler.post (new Runnable()
          {
           public void run()
            {
              text_view.setText ("Media explorer remote:" + response);
              web_view.loadUrl (response);
             }
           });

        if (jmdns != null)
          {
            if (listener != null)
              {
                jmdns.removeServiceListener ("_http._tcp.local.", listener);
                listener = null;
              }
            jmdns = null;
          }
      }

    @Override
    protected void onStop ()
      {
        if (jmdns != null)
          {
            if (listener != null)
              {
                jmdns.removeServiceListener ("_http._tcp.local.", listener);
                listener = null;
              }
            jmdns = null;
          }
        super.onStop ();
    }
}
