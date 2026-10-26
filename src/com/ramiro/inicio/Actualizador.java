package com.ramiro.inicio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Busca versiones nuevas en GitHub (version.json) y las instala con un OK.
 * También recibe el resultado de la instalación.
 */
public class Actualizador extends BroadcastReceiver {

    static final String VERSION_URL = "https://ramiroagi.github.io/tv/version.json";

    /** Se fija si hay versión nueva; si la hay, pregunta. */
    static void revisar(final Activity act) {
        new Thread(() -> {
            try {
                JSONObject v = new JSONObject(MainActivity.bajarTexto(VERSION_URL + "?t=" + System.currentTimeMillis()));
                int remota = v.getInt("versionCode");
                int local = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode;
                if (remota <= local) return;
                final String nombre = v.optString("versionName", "");
                final String apk = v.getString("apk");
                act.runOnUiThread(() -> {
                    if (act.isFinishing()) return;
                    new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Hay una versión nueva de Inicio (" + nombre + ")")
                            .setMessage("¿La instalo ahora? Tarda unos segundos.")
                            .setPositiveButton("Instalar", (d, w) -> instalar(act, apk))
                            .setNegativeButton("Más tarde", null)
                            .show();
                });
            } catch (Exception ignored) {
                // Sin internet o sin archivo: se vuelve a probar más tarde
            }
        }).start();
    }

    private static void instalar(final Activity act, final String apk) {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(act, "Activá el permiso para Inicio, volvé y apretá Instalar de nuevo",
                    Toast.LENGTH_LONG).show();
            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName())));
            return;
        }
        Toast.makeText(act, "Bajando la actualización…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                PackageInstaller pi = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(act.getPackageName());
                int id = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection c = (HttpURLConnection) new URL(apk).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
                    try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("inicio.apk", 0, -1)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    } finally {
                        c.disconnect();
                    }
                    Intent resultado = new Intent(act, Actualizador.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                    s.commit(PendingIntent.getBroadcast(act, id, resultado, flags).getIntentSender());
                }
            } catch (Exception e) {
                act.runOnUiThread(() -> Toast.makeText(act, "No se pudo actualizar: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        int estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android pide confirmar la instalación: mostrar su pantalla
            Intent confirmar = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmar != null) c.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (estado != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "La actualización no se instaló", Toast.LENGTH_LONG).show();
        }
    }
}
