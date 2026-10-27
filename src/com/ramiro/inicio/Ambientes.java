package com.ramiro.inicio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Video con sonido a pantalla completa: mar, lluvia, chimenea, bosque.
 * Izquierda/derecha cambia de ambiente, OK pone un temporizador para dormir, cualquier otra tecla sale.
 */
public class Ambientes extends Activity {

    static final String EXTRA_NOCHE = "noche";

    private static final String WM = "https://upload.wikimedia.org/wikipedia/commons/";

    // nombre, video de día, video de noche (o null), archivo de día, archivo de noche, crédito de día, crédito de noche
    static final String[][] AMBIENTES = {
            {"Mar",
                    WM + "transcoded/7/75/Groyne_and_sea_waves_at_Curonian_Spit.webm/Groyne_and_sea_waves_at_Curonian_Spit.webm.1080p.vp9.webm",
                    WM + "transcoded/2/24/Waves_crashing_on_rocks_off_Beach_4%2C_Kalaloch_Beach%2C_Washington_01.webm/"
                            + "Waves_crashing_on_rocks_off_Beach_4%2C_Kalaloch_Beach%2C_Washington_01.webm.1080p.vp9.webm",
                    "mar_dia.webm", "mar_noche.webm",
                    "Curonian Spit, mar Báltico · Video: Alexander Grebenkov, CC BY 3.0",
                    "Kalaloch, Washington · Video: Joe Mabel, CC BY-SA 4.0"},
            {"Lluvia",
                    WM + "transcoded/c/cc/Radevormwald_-_Raindrops_on_a_window_07_%281%29_ies.webm/"
                            + "Radevormwald_-_Raindrops_on_a_window_07_%281%29_ies.webm.1080p.vp9.webm",
                    null, "lluvia.webm", null,
                    "Lluvia en Radevormwald · Video: Frank Vincentz, CC BY-SA 3.0", null},
            {"Chimenea",
                    WM + "transcoded/b/bb/Fantastic-fireplace-fire-chimney-hearth-_background_-_texture_-_motion_graphics_-_free_video_library.webm/"
                            + "Fantastic-fireplace-fire-chimney-hearth-_background_-_texture_-_motion_graphics_-_free_video_library.webm.1080p.vp9.webm",
                    null, "chimenea.webm", null,
                    "Chimenea · Video: free video library, CC BY 3.0", null},
            {"Bosque",
                    WM + "5/5f/Autumn_stream_in_West_Virginia.webm",
                    null, "bosque.webm", null,
                    "Arroyo en Virginia Occidental · Video: Forest Wander, CC BY-SA 2.0", null},
    };

    // De noche el video se apaga un poco
    static final float OSCURIDAD_NOCHE = 0.25f;
    static final int SUBIDA_VOLUMEN_MS = 4000;
    // Al terminar el temporizador: el sonido y la imagen se van apagando durante este tiempo
    static final int FUNDIDO_FINAL_MS = 60 * 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean noche;
    private int actual;
    private VideoView video;
    private ImageView foto;
    private View negro;
    private TextView titulo, estado, credito;
    private MediaPlayer reproductor;
    private float volumen = 0f;
    private volatile int descarga; // cambia cada vez que se pide otro video, para cortar descargas viejas
    private boolean apagando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        noche = getIntent().getBooleanExtra(EXTRA_NOCHE, false);
        actual = Math.max(0, Math.min(AMBIENTES.length - 1, prefs.getInt("ambiente", 0)));

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);

        // Mientras baja o carga el mar, se ve la foto fija de siempre
        foto = new ImageView(this);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        foto.setImageResource(noche ? R.drawable.fondo_noche : R.drawable.fondo_dia);
        raiz.addView(foto, new FrameLayout.LayoutParams(-1, -1));

        video = new VideoView(this);
        raiz.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        if (noche) {
            View sombra = new View(this);
            sombra.setBackgroundColor(Color.BLACK);
            sombra.setAlpha(OSCURIDAD_NOCHE);
            raiz.addView(sombra, new FrameLayout.LayoutParams(-1, -1));
        }

        titulo = texto(22, Color.WHITE);
        FrameLayout.LayoutParams lpt = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lpt.topMargin = dp(36);
        raiz.addView(titulo, lpt);

        estado = texto(18, 0xEEFFFFFF);
        raiz.addView(estado, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        credito = texto(10, 0x99FFFFFF);
        FrameLayout.LayoutParams lpc = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        lpc.setMargins(0, 0, dp(12), dp(10));
        raiz.addView(credito, lpc);

        // Capa para el fundido a negro del temporizador
        negro = new View(this);
        negro.setBackgroundColor(Color.BLACK);
        negro.setAlpha(0f);
        raiz.addView(negro, new FrameLayout.LayoutParams(-1, -1));

        setContentView(raiz);
        mostrar(actual, "◀ ▶ cambiar ambiente   ·   OK temporizador");
    }

    // ---------------- Ambientes ----------------

    private void mostrar(int cual, String ayuda) {
        actual = cual;
        prefs.edit().putInt("ambiente", cual).apply();
        String[] a = AMBIENTES[cual];
        boolean versionNoche = noche && a[2] != null;

        video.stopPlayback();
        reproductor = null;
        foto.setAlpha(cual == 0 ? 1f : 0f); // la foto de fondo solo tiene sentido para el mar
        estado.setText("");
        avisar(titulo, a[0] + (ayuda != null ? "\n" + ayuda : ""), 4000);
        avisar(credito, versionNoche ? a[6] : a[5], 8000);

        File archivo = new File(getFilesDir(), versionNoche ? a[4] : a[3]);
        if (archivo.exists()) {
            reproducir(archivo);
        } else {
            estado.setText("Bajando " + a[0].toLowerCase() + "… (solo la primera vez)");
            bajar(versionNoche ? a[2] : a[1], archivo);
        }
    }

    private void bajar(final String url, final File destino) {
        final int mia = ++descarga;
        new Thread(() -> {
            File tmp = new File(destino.getPath() + ".tmp");
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "InicioTV/1.0 (github.com/ramiroAGI/tv)");
                long total = c.getContentLength();
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    long llevo = 0;
                    int n, ultimo = -1;
                    while ((n = in.read(buf)) > 0) {
                        if (mia != descarga) return; // cambió de ambiente o salió: se retoma de cero la próxima
                        out.write(buf, 0, n);
                        llevo += n;
                        final int pct = total > 0 ? (int) (llevo * 100 / total) : 0;
                        if (pct != ultimo) {
                            ultimo = pct;
                            runOnUiThread(() -> {
                                if (mia == descarga) estado.setText("Bajando… " + pct + "%\n(solo la primera vez)");
                            });
                        }
                    }
                } finally {
                    c.disconnect();
                }
                if (mia == descarga && tmp.renameTo(destino)) {
                    runOnUiThread(() -> {
                        if (mia == descarga) reproducir(destino);
                    });
                }
            } catch (Exception e) {
                tmp.delete();
                runOnUiThread(() -> {
                    if (mia == descarga) estado.setText("No pude bajar el video. Revisá internet y probá de nuevo.");
                });
            }
        }).start();
    }

    private void reproducir(File archivo) {
        estado.setText("");
        video.setOnPreparedListener(mp -> {
            reproductor = mp;
            mp.setLooping(true);
            volumen = 0f;
            mp.setVolume(0f, 0f);
            handler.removeCallbacks(subirVolumen);
            handler.post(subirVolumen);
            foto.animate().alpha(0f).setDuration(1500).start();
        });
        video.setOnErrorListener((mp, que, extra) -> {
            estado.setText("Esta tele no pudo reproducir el video");
            return true;
        });
        video.setVideoPath(archivo.getPath());
        video.start();
    }

    /** El sonido entra de a poco, no de golpe. */
    private final Runnable subirVolumen = new Runnable() {
        @Override
        public void run() {
            if (reproductor == null || apagando) return;
            volumen = Math.min(1f, volumen + 100f / SUBIDA_VOLUMEN_MS);
            try {
                reproductor.setVolume(volumen, volumen);
            } catch (Exception e) {
                return;
            }
            if (volumen < 1f) handler.postDelayed(this, 100);
        }
    };

    // ---------------- Temporizador para dormir ----------------

    private void elegirTemporizador() {
        final int[] minutos = {30, 60, 90, 0};
        String[] opciones = {"30 minutos", "60 minutos", "90 minutos", "Sin temporizador"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("¿Apagar en…?")
                .setItems(opciones, (d, cual) -> {
                    handler.removeCallbacks(empezarApagado);
                    if (minutos[cual] == 0) {
                        avisar(titulo, "Sin temporizador", 3000);
                    } else {
                        handler.postDelayed(empezarApagado, minutos[cual] * 60 * 1000L);
                        avisar(titulo, "Se apaga en " + minutos[cual] + " minutos", 3000);
                    }
                })
                .show();
    }

    private final Runnable empezarApagado = new Runnable() {
        @Override
        public void run() {
            apagando = true;
            final float desde = volumen;
            final long inicio = System.currentTimeMillis();
            negro.animate().alpha(1f).setDuration(FUNDIDO_FINAL_MS).start();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    float avance = Math.min(1f, (System.currentTimeMillis() - inicio) / (float) FUNDIDO_FINAL_MS);
                    float v = desde * (1f - avance);
                    try {
                        if (reproductor != null) reproductor.setVolume(v, v);
                    } catch (Exception ignored) {
                    }
                    if (avance < 1f) {
                        handler.postDelayed(this, 200);
                    } else {
                        terminarApagado();
                    }
                }
            });
        }
    };

    private void terminarApagado() {
        video.stopPlayback();
        reproductor = null;
        // Que la pantalla pueda apagarse; si la tele lo permite, pasarla a reposo ya
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Vigia.dormir();
    }

    // ---------------- Teclas ----------------

    @Override
    public boolean onKeyDown(int codigo, KeyEvent e) {
        switch (codigo) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return super.onKeyDown(codigo, e);
        }
        if (apagando) {
            finish(); // se estaba apagando y tocó algo: volver al inicio
            return true;
        }
        switch (codigo) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mostrar((actual + AMBIENTES.length - 1) % AMBIENTES.length, null);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mostrar((actual + 1) % AMBIENTES.length, null);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                elegirTemporizador();
                return true;
            default:
                finish();
                return true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Si se va a otra app o se apaga la pantalla, el ambiente se termina
        finish();
    }

    @Override
    protected void onDestroy() {
        descarga++;
        handler.removeCallbacksAndMessages(null);
        video.stopPlayback();
        super.onDestroy();
    }

    // ---------------- Utilidades ----------------

    /** Muestra un texto y lo hace desaparecer después de un rato. */
    private void avisar(final TextView t, String texto, int ms) {
        t.animate().cancel();
        t.setText(texto);
        t.setAlpha(1f);
        t.animate().alpha(0f).setStartDelay(ms).setDuration(1500).start();
    }

    private TextView texto(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(8, 0, 0, 0x99000000);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
