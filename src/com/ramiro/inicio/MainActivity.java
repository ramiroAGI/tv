package com.ramiro.inicio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // ---- Configuración: lo que se puede tocar fácil ----

    // Clima de Buenos Aires
    static final double LATITUD = -34.61;
    static final double LONGITUD = -58.38;

    // Modo noche: entre estas horas la pantalla se oscurece
    static final int NOCHE_DESDE = 22;
    static final int NOCHE_HASTA = 7;
    static final float NOCHE_OSCURIDAD = 0.5f;

    // Apps: nombre, paquetes posibles (cambian según la versión), palabras para buscarla por nombre
    static final String[][] APPS = {
            {"Netflix", "com.netflix.ninja,com.netflix.mediaclient", "netflix"},
            {"YouTube", "com.google.android.youtube.tv,com.google.android.youtube", "youtube"},
            {"Disney+", "com.disney.disneyplus", "disney"},
            {"Max", "com.wbd.stream,com.hbo.hbonow,com.hbo.max.android.tv", "max,hbo"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom,com.amazon.avod.thirdpartyclient", "prime"},
            {"Paramount+", "com.cbs.ott,com.cbs.app", "paramount"},
            {"Spotify", "com.spotify.tv.android,com.spotify.music", "spotify"},
    };

    // ---------------------------------------------------

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ImageView fondo;
    private TextView temperatura, clima, credito;
    private View noche;
    private LinearLayout fila1, fila2;
    private boolean faltanApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        fondo = findViewById(R.id.fondo);
        temperatura = findViewById(R.id.temperatura);
        clima = findViewById(R.id.clima);
        credito = findViewById(R.id.credito);
        noche = findViewById(R.id.noche);
        fila1 = findViewById(R.id.fila1);
        fila2 = findViewById(R.id.fila2);

        armarBotones();
        cargarFondo();
        handler.post(actualizarClima);
        handler.post(actualizarNoche);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si faltaba alguna app y la instalaron, rearmar los botones
        if (faltanApps) armarBotones();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Al apretar "Inicio" en el control, volver al primer botón
        if (fila1.getChildCount() > 0) fila1.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Somos la pantalla de inicio: "Atrás" no hace nada
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------- Botones ----------------

    private void armarBotones() {
        fila1.removeAllViews();
        fila2.removeAllViews();
        faltanApps = false;
        List<ResolveInfo> instaladas = listarApps();

        List<View> botones = new ArrayList<>();
        for (String[] app : APPS) {
            final String nombre = app[0];
            final String paquete = buscarPaquete(app[1].split(","), app[2].split(","), instaladas);
            if (paquete != null) {
                Drawable banner = cargarBanner(paquete);
                botones.add(crearBoton(nombre, banner, banner == null ? cargarIcono(paquete) : null,
                        null, v -> abrirApp(paquete), null, false));
            } else {
                faltanApps = true;
                final String sugerido = app[1].split(",")[0];
                botones.add(crearBoton(nombre, null, null, null,
                        v -> abrirTienda(sugerido), null, true));
            }
        }
        botones.add(crearBoton("PlayStation", null, null, colorPlay(),
                v -> abrirPlay(false), v -> { abrirPlay(true); return true; }, false));

        for (int i = 0; i < botones.size(); i++) {
            (i < 4 ? fila1 : fila2).addView(botones.get(i));
        }
        botones.get(0).requestFocus();
    }

    private View crearBoton(String nombre, Drawable banner, Drawable icono, Drawable fondoPropio,
                            View.OnClickListener click, View.OnLongClickListener clickLargo,
                            boolean falta) {
        FrameLayout b = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190), dp(107));
        lp.setMargins(dp(10), dp(10), dp(10), dp(10));
        b.setLayoutParams(lp);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(fondoPropio != null ? fondoPropio : rect(0xCC1C1C1E, 0, 0));
        b.setForeground(bordeFoco());
        b.setClipToOutline(true);
        b.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageDrawable(banner);
            b.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            if (icono != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icono);
                col.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            TextView t = new TextView(this);
            t.setText(falta ? nombre + "\n(no instalada)" : nombre);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, falta ? 15 : 20);
            col.addView(t);
            b.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }
        if (falta) b.setAlpha(0.5f);

        b.setOnClickListener(click);
        if (clickLargo != null) b.setOnLongClickListener(clickLargo);
        b.setOnFocusChangeListener((v, foco) -> {
            float escala = foco ? 1.1f : 1f;
            v.animate().scaleX(escala).scaleY(escala).translationZ(foco ? dp(8) : 0).setDuration(120).start();
        });
        return b;
    }

    private Drawable bordeFoco() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rect(Color.TRANSPARENT, dp(3), Color.WHITE));
        s.addState(new int[]{}, rect(Color.TRANSPARENT, 0, 0));
        return s;
    }

    private Drawable colorPlay() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF003791, 0xFF0070D1});
        g.setCornerRadius(dp(10));
        return g;
    }

    private GradientDrawable rect(int color, int borde, int colorBorde) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        if (borde > 0) g.setStroke(borde, colorBorde);
        return g;
    }

    // ---------------- Apps ----------------

    private List<ResolveInfo> listarApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> lista = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        lista.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        return lista;
    }

    private String buscarPaquete(String[] paquetes, String[] palabras, List<ResolveInfo> instaladas) {
        for (String p : paquetes) {
            for (ResolveInfo r : instaladas) {
                if (r.activityInfo.packageName.equals(p)) return p;
            }
        }
        // Si cambió el paquete, buscar por nombre visible
        PackageManager pm = getPackageManager();
        for (ResolveInfo r : instaladas) {
            String etiqueta = r.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            for (String palabra : palabras) {
                if (etiqueta.equals(palabra) || etiqueta.startsWith(palabra + " ")
                        || etiqueta.contains(" " + palabra) || etiqueta.startsWith(palabra)) {
                    return r.activityInfo.packageName;
                }
            }
        }
        return null;
    }

    private Drawable cargarBanner(String paquete) {
        PackageManager pm = getPackageManager();
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage(paquete);
            if (i != null) {
                Drawable d = pm.getActivityBanner(i);
                if (d != null) return d;
            }
            return pm.getApplicationBanner(paquete);
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable cargarIcono(String paquete) {
        try {
            return getPackageManager().getApplicationIcon(paquete);
        } catch (Exception e) {
            return null;
        }
    }

    private void abrirApp(String paquete) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(paquete);
        if (i == null) i = pm.getLaunchIntentForPackage(paquete);
        if (i == null) {
            aviso("No pude abrir la app");
            return;
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            aviso("No pude abrir la app");
        }
    }

    private void abrirTienda(String paquete) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + paquete)));
        } catch (Exception e) {
            aviso("Instalala desde la Play Store");
        }
    }

    // ---------------- PlayStation (HDMI) ----------------

    private void abrirPlay(boolean elegir) {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) {
            aviso("Esta tele no permite cambiar de entrada desde una app");
            return;
        }
        final List<TvInputInfo> hdmis = new ArrayList<>();
        for (TvInputInfo i : tim.getTvInputList()) {
            if (i.isPassthroughInput() && i.getType() == TvInputInfo.TYPE_HDMI) hdmis.add(i);
        }
        if (hdmis.isEmpty()) {
            aviso("No encontré entradas HDMI");
            return;
        }

        if (!elegir) {
            String guardada = prefs.getString("hdmi_play", null);
            for (TvInputInfo i : hdmis) {
                if (i.getId().equals(guardada)) {
                    verEntrada(i.getId());
                    return;
                }
            }
            // Si la Play se anuncia por HDMI-CEC, reconocerla sola
            for (TvInputInfo i : hdmis) {
                String n = nombreEntrada(i).toLowerCase(Locale.ROOT);
                if (n.contains("playstation") || n.contains("ps4") || n.contains("ps5")) {
                    guardarYVer(i.getId());
                    return;
                }
            }
        }

        String[] nombres = new String[hdmis.size()];
        for (int i = 0; i < hdmis.size(); i++) nombres[i] = nombreEntrada(hdmis.get(i));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("¿En qué entrada está la PlayStation?")
                .setItems(nombres, (d, cual) -> guardarYVer(hdmis.get(cual).getId()))
                .show();
    }

    private String nombreEntrada(TvInputInfo i) {
        CharSequence n = null;
        if (Build.VERSION.SDK_INT >= 24) n = i.loadCustomLabel(this);
        if (n == null || n.length() == 0) n = i.loadLabel(this);
        return n.toString();
    }

    private void guardarYVer(String id) {
        prefs.edit().putString("hdmi_play", id).apply();
        aviso("Listo. Para cambiar la entrada, mantené apretado el botón PlayStation");
        verEntrada(id);
    }

    private void verEntrada(String id) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
        } catch (Exception e) {
            aviso("La tele no dejó cambiar al HDMI. Usá el botón de entradas del control.");
        }
    }

    // ---------------- Fondo (foto del día de Bing) ----------------

    private void cargarFondo() {
        new Thread(() -> {
            File archivo = new File(getFilesDir(), "fondo.jpg");
            if (archivo.exists()) mostrarFondo(archivo);

            String hoy = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
            if (hoy.equals(prefs.getString("fondo_dia", "")) && archivo.exists()) return;
            try {
                JSONObject img = new JSONObject(bajarTexto(
                        "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=es-ES"))
                        .getJSONArray("images").getJSONObject(0);
                File tmp = new File(getFilesDir(), "fondo.tmp");
                bajarArchivo("https://www.bing.com" + img.getString("urlbase") + "_1920x1080.jpg", tmp);
                if (tmp.renameTo(archivo)) {
                    prefs.edit().putString("fondo_dia", hoy)
                            .putString("fondo_credito", img.optString("copyright")).apply();
                    mostrarFondo(archivo);
                }
            } catch (Exception ignored) {
                // Sin internet: queda el fondo anterior o el degradé
            }
        }).start();
    }

    private void mostrarFondo(File archivo) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565; // la mitad de memoria; en una foto no se nota
        int anchoPantalla = getResources().getDisplayMetrics().widthPixels;
        if (anchoPantalla < 1600) o.inSampleSize = 2;
        final Bitmap bmp = BitmapFactory.decodeFile(archivo.getPath(), o);
        final String txt = prefs.getString("fondo_credito", "");
        if (bmp == null) return;
        runOnUiThread(() -> {
            fondo.setImageBitmap(bmp);
            fondo.setAlpha(0f);
            fondo.animate().alpha(1f).setDuration(600).start();
            credito.setText(txt);
        });
    }

    // ---------------- Clima (Open-Meteo, sin cuenta) ----------------

    private final Runnable actualizarClima = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                try {
                    JSONObject j = new JSONObject(bajarTexto(String.format(Locale.ROOT,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                                    + "&current=temperature_2m,weather_code,is_day"
                                    + "&daily=temperature_2m_max,temperature_2m_min"
                                    + "&timezone=auto&forecast_days=1", LATITUD, LONGITUD)));
                    JSONObject ahora = j.getJSONObject("current");
                    JSONObject dia = j.getJSONObject("daily");
                    final String temp = Math.round(ahora.getDouble("temperature_2m")) + "°";
                    final String desc = describirClima(ahora.getInt("weather_code"), ahora.getInt("is_day") == 1)
                            + "   ↑" + Math.round(dia.getJSONArray("temperature_2m_max").getDouble(0)) + "°"
                            + "  ↓" + Math.round(dia.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
                    runOnUiThread(() -> {
                        temperatura.setText(temp);
                        clima.setText(desc);
                    });
                } catch (Exception ignored) {
                    // Sin internet: queda el último dato
                }
            }).start();
            handler.postDelayed(this, 30 * 60 * 1000);
        }
    };

    private static String describirClima(int codigo, boolean esDeDia) {
        if (codigo == 0) return esDeDia ? "☀ Despejado" : "☾ Despejado";
        if (codigo <= 2) return "⛅ Algo nublado";
        if (codigo == 3) return "☁ Nublado";
        if (codigo <= 48) return "🌫 Niebla";
        if (codigo <= 57) return "🌦 Llovizna";
        if (codigo <= 67) return "🌧 Lluvia";
        if (codigo <= 77) return "❄ Nieve";
        if (codigo <= 82) return "🌧 Chaparrones";
        return "⛈ Tormenta";
    }

    // ---------------- Modo noche ----------------

    private final Runnable actualizarNoche = new Runnable() {
        @Override
        public void run() {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean esNoche = h >= NOCHE_DESDE || h < NOCHE_HASTA;
            float destino = esNoche ? NOCHE_OSCURIDAD : 0f;
            if (noche.getAlpha() != destino) noche.animate().alpha(destino).setDuration(2000).start();
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------------- Utilidades ----------------

    private static HttpURLConnection conectar(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Inicio-TV");
        return c;
    }

    private static String bajarTexto(String url) throws Exception {
        HttpURLConnection c = conectar(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static void bajarArchivo(String url, File destino) throws Exception {
        HttpURLConnection c = conectar(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(destino)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    private void aviso(String texto) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
