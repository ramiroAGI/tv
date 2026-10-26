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
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // ---- Configuración: lo que se puede tocar fácil ----

    // Clima de Buenos Aires
    static final double LATITUD = -34.61;
    static final double LONGITUD = -58.38;

    // Modo noche: empieza con el atardecer real y termina con el amanecer (datos del clima).
    // Si todavía no hay datos, se usan estas horas de reserva.
    static final int NOCHE_DESDE = 20;
    static final int NOCHE_HASTA = 7;
    static final float NOCHE_OSCURIDAD = 0.35f;

    // Modo "solo fondo": sin tocar el control este tiempo, los botones bajan y quedan asomando
    static final int ESCONDER_BOTONES_SEGUNDOS = 30;
    static final int BOTONES_ASOMAN_DP = 30;

    // Cotizaciones (de día; de noche se ocultan para desconectar). false = no mostrarlas nunca
    static final boolean MOSTRAR_MERCADO = true;
    static final String[][] COTIZACIONES = {
            // símbolo en Yahoo, nombre en pantalla, decimales
            {"QQQ", "QQQ", "2"},
            {"^MERV", "MERVAL", "0"},
    };

    static final String CREDITO_DIA = "Cape Otway, Australia · Foto: Dietmar Rabich, CC BY-SA 4.0";
    static final String CREDITO_NOCHE = "Hong Kong de noche · Foto: Alex Wong, CC0";

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
    private TextView temperatura, clima, mercado, credito;
    private View noche;
    private LinearLayout botonera, fila1, fila2;
    private boolean faltanApps;
    private boolean botonesEscondidos;
    private boolean preguntoVigia;
    private Boolean modoNoche; // null hasta la primera vez

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        fondo = findViewById(R.id.fondo);
        temperatura = findViewById(R.id.temperatura);
        clima = findViewById(R.id.clima);
        mercado = findViewById(R.id.mercado);
        credito = findViewById(R.id.credito);
        noche = findViewById(R.id.noche);
        botonera = findViewById(R.id.botonera);
        fila1 = findViewById(R.id.fila1);
        fila2 = findViewById(R.id.fila2);

        findViewById(R.id.todas).setOnClickListener(v -> mostrarTodasLasApps());
        findViewById(R.id.ajustes).setOnClickListener(v -> abrirAjustes());
        for (int id : new int[]{R.id.todas, R.id.ajustes}) {
            findViewById(id).setOnFocusChangeListener((v, foco) -> v.setAlpha(foco ? 1f : 0.6f));
        }

        armarBotones();
        handler.post(actualizarNoche);
        handler.post(actualizarClima);
        if (MOSTRAR_MERCADO) handler.post(actualizarMercado);
        handler.post(buscarActualizacion);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si faltaba alguna app y la instalaron, rearmar los botones
        if (faltanApps) armarBotones();
        mostrarBotones(false);
        if (!preguntoVigia && !vigiaActiva() && !prefs.getBoolean("vigia_no_preguntar", false)) {
            preguntoVigia = true;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("¿Usar esta pantalla al apretar Inicio?")
                    .setMessage("Para eso hay que activar \"Inicio: botón Inicio\" en Accesibilidad. "
                            + "Android va a mostrar un aviso general; esta app solo detecta cuándo aparece "
                            + "la pantalla de Google. Se desactiva en el mismo lugar cuando quieras.")
                    .setPositiveButton("Activar", (d, w) -> abrirAccesibilidad())
                    .setNeutralButton("No volver a preguntar",
                            (d, w) -> prefs.edit().putBoolean("vigia_no_preguntar", true).apply())
                    .setNegativeButton("Ahora no", null)
                    .show();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(esconderBotones);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Al apretar "Inicio" en el control, volver al primer botón
        mostrarBotones(false);
        if (fila1.getChildCount() > 0) fila1.getChildAt(0).requestFocus();
    }

    // La tecla que muestra/esconde los botones no tiene que hacer nada más (ni al mantenerla ni al soltarla)
    private boolean tragarHastaSoltar;

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (tragarHastaSoltar) {
            if (e.getAction() == KeyEvent.ACTION_UP) tragarHastaSoltar = false;
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (botonesEscondidos) {
                mostrarBotones(true);
                tragarHastaSoltar = true;
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && fila2.hasFocus()) {
                esconderBotones.run();
                tragarHastaSoltar = true;
                return true;
            }
            reiniciarEspera();
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------- Modo "solo fondo" ----------------

    private final Runnable esconderBotones = new Runnable() {
        @Override
        public void run() {
            if (botonesEscondidos || botonera.getHeight() == 0) return;
            botonesEscondidos = true;
            float bajar = botonera.getHeight() + ((View) botonera.getParent()).getPaddingBottom() - dp(BOTONES_ASOMAN_DP);
            botonera.animate().translationY(bajar).alpha(0.6f).setDuration(900).start();
        }
    };

    private void mostrarBotones(boolean animar) {
        botonesEscondidos = false;
        botonera.animate().cancel();
        if (animar) {
            botonera.animate().translationY(0).alpha(1f).setDuration(300).start();
        } else {
            botonera.setTranslationY(0);
            botonera.setAlpha(1f);
        }
        reiniciarEspera();
    }

    private void reiniciarEspera() {
        handler.removeCallbacks(esconderBotones);
        handler.postDelayed(esconderBotones, ESCONDER_BOTONES_SEGUNDOS * 1000L);
    }

    // ---------------- Accesibilidad (botón Inicio) ----------------

    private boolean vigiaActiva() {
        String activos = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return activos != null && activos.contains(getPackageName() + "/");
    }

    private void abrirAccesibilidad() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            aviso("Buscá \"Inicio: botón Inicio\" y activalo");
        } catch (Exception e) {
            aviso("Andá a Ajustes → Accesibilidad → Inicio: botón Inicio");
        }
    }

    // ---------------- Todas las apps y ajustes ----------------

    private void mostrarTodasLasApps() {
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        final List<String> paquetes = new ArrayList<>();
        final List<String> nombres = new ArrayList<>();
        for (ResolveInfo r : apps) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            if (paquetes.contains(r.activityInfo.packageName)) continue;
            paquetes.add(r.activityInfo.packageName);
            nombres.add(r.loadLabel(pm).toString());
        }
        // Orden alfabético
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) orden.add(i);
        java.util.Collections.sort(orden, (a, b) -> nombres.get(a).compareToIgnoreCase(nombres.get(b)));
        final String[] etiquetas = new String[orden.size()];
        final String[] pkgs = new String[orden.size()];
        for (int i = 0; i < orden.size(); i++) {
            etiquetas[i] = nombres.get(orden.get(i));
            pkgs[i] = paquetes.get(orden.get(i));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Todas las apps")
                .setItems(etiquetas, (d, cual) -> abrirApp(pkgs[cual]))
                .show();
    }

    private void abrirAjustes() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            aviso("No pude abrir los ajustes");
        }
    }

    private final Runnable buscarActualizacion = new Runnable() {
        @Override
        public void run() {
            Actualizador.revisar(MainActivity.this);
            handler.postDelayed(this, 6 * 60 * 60 * 1000L);
        }
    };

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
        botones.add(crearBoton("PlayStation", null, null, colorPlay(),
                v -> abrirPlay(false), v -> { abrirPlay(true); return true; }, false));
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

    // ---------------- Fondo: mar de día, ciudad de noche ----------------

    private void cargarFondo(final boolean deNoche) {
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565; // la mitad de memoria; en una foto no se nota
            o.inScaled = false;
            if (getResources().getDisplayMetrics().widthPixels < 1600) o.inSampleSize = 2;
            final Bitmap bmp = BitmapFactory.decodeResource(getResources(),
                    deNoche ? R.drawable.fondo_noche : R.drawable.fondo_dia, o);
            if (bmp == null) return;
            runOnUiThread(() -> {
                fondo.animate().alpha(0f).setDuration(fondo.getDrawable() == null ? 0 : 800)
                        .withEndAction(() -> {
                            fondo.setImageBitmap(bmp);
                            credito.setText(deNoche ? CREDITO_NOCHE : CREDITO_DIA);
                            fondo.animate().alpha(1f).setDuration(800).start();
                        }).start();
            });
        }).start();
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
                                    + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                                    + "&timezone=auto&forecast_days=1", LATITUD, LONGITUD)));
                    JSONObject ahora = j.getJSONObject("current");
                    JSONObject dia = j.getJSONObject("daily");
                    // Vienen como "2026-10-26T19:43": guardar los minutos del día
                    prefs.edit()
                            .putInt("amanecer", minutosDelDia(dia.getJSONArray("sunrise").getString(0)))
                            .putInt("atardecer", minutosDelDia(dia.getJSONArray("sunset").getString(0)))
                            .apply();
                    runOnUiThread(MainActivity.this::revisarNoche);
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
            revisarNoche();
            handler.postDelayed(this, 60 * 1000);
        }
    };

    private void revisarNoche() {
        Calendar c = Calendar.getInstance();
        int ahora = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int amanecer = prefs.getInt("amanecer", NOCHE_HASTA * 60);
        int atardecer = prefs.getInt("atardecer", NOCHE_DESDE * 60);
        boolean esNoche = ahora >= atardecer || ahora < amanecer;
        if (modoNoche == null || modoNoche != esNoche) {
            modoNoche = esNoche;
            cargarFondo(esNoche);
            noche.animate().alpha(esNoche ? NOCHE_OSCURIDAD : 0f).setDuration(2000).start();
            mercado.setVisibility(esNoche || !MOSTRAR_MERCADO ? View.GONE : View.VISIBLE);
        }
    }

    private static int minutosDelDia(String fechaHora) {
        String hhmm = fechaHora.substring(fechaHora.indexOf('T') + 1);
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    // ---------------- Cotizaciones (Yahoo Finance) ----------------

    private final Runnable actualizarMercado = new Runnable() {
        @Override
        public void run() {
            if (!Boolean.TRUE.equals(modoNoche)) {
                new Thread(() -> {
                    final SpannableStringBuilder txt = new SpannableStringBuilder();
                    for (String[] c : COTIZACIONES) {
                        try {
                            double[] precios = ultimosCierres(c[0]);
                            double variacion = (precios[1] / precios[0] - 1) * 100;
                            if (txt.length() > 0) txt.append("     ");
                            txt.append(c[1]).append(" ");
                            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "AR"));
                            int dec = Integer.parseInt(c[2]);
                            nf.setMinimumFractionDigits(dec);
                            nf.setMaximumFractionDigits(dec);
                            txt.append(nf.format(precios[1])).append(" ");
                            int ini = txt.length();
                            txt.append(String.format(new Locale("es", "AR"), "%s%.1f%%",
                                    variacion >= 0 ? "▲" : "▼", Math.abs(variacion)));
                            txt.setSpan(new ForegroundColorSpan(variacion >= 0 ? 0xFF7CE38B : 0xFFFF7B72),
                                    ini, txt.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } catch (Exception ignored) {
                            // Si una falla, se muestran las demás
                        }
                    }
                    if (txt.length() > 0) runOnUiThread(() -> mercado.setText(txt));
                }).start();
            }
            handler.postDelayed(this, 10 * 60 * 1000);
        }
    };

    /** Devuelve {cierre anterior, último precio} a partir de las velas diarias. */
    private static double[] ultimosCierres(String simbolo) throws Exception {
        JSONObject j = new JSONObject(bajarTexto("https://query1.finance.yahoo.com/v8/finance/chart/"
                + Uri.encode(simbolo) + "?range=5d&interval=1d"));
        JSONArray cierres = j.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                .getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");
        List<Double> validos = new ArrayList<>();
        for (int i = 0; i < cierres.length(); i++) {
            if (!cierres.isNull(i)) validos.add(cierres.getDouble(i));
        }
        if (validos.size() < 2) throw new Exception("sin datos");
        return new double[]{validos.get(validos.size() - 2), validos.get(validos.size() - 1)};
    }

    // ---------------- Utilidades ----------------

    private static HttpURLConnection conectar(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Inicio-TV");
        return c;
    }

    static String bajarTexto(String url) throws Exception {
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

    private void aviso(String texto) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
