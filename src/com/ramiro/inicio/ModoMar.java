package com.ramiro.inicio;

import android.app.Activity;
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

/** Mar en movimiento a pantalla completa, con el sonido de las olas. Cualquier tecla sale. */
public class ModoMar extends Activity {

    static final String EXTRA_NOCHE = "noche";

    // Videos de Wikimedia Commons (se bajan la primera vez y quedan guardados)
    static final String VIDEO_DIA = "https://upload.wikimedia.org/wikipedia/commons/transcoded/7/75/"
            + "Groyne_and_sea_waves_at_Curonian_Spit.webm/Groyne_and_sea_waves_at_Curonian_Spit.webm.1080p.vp9.webm";
    static final String VIDEO_NOCHE = "https://upload.wikimedia.org/wikipedia/commons/transcoded/2/24/"
            + "Waves_crashing_on_rocks_off_Beach_4%2C_Kalaloch_Beach%2C_Washington_01.webm/"
            + "Waves_crashing_on_rocks_off_Beach_4%2C_Kalaloch_Beach%2C_Washington_01.webm.1080p.vp9.webm";
    static final String CREDITO_DIA = "Curonian Spit, mar Báltico · Video: Alexander Grebenkov, CC BY 3.0";
    static final String CREDITO_NOCHE = "Kalaloch, Washington · Video: Joe Mabel, CC BY-SA 4.0";

    // Al anochecer el video ya es oscuro; esto lo apaga un poco más
    static final float OSCURIDAD_NOCHE = 0.25f;
    static final int SUBIDA_VOLUMEN_MS = 4000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView video;
    private TextView estado, credito;
    private volatile boolean cerrando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean noche = getIntent().getBooleanExtra(EXTRA_NOCHE, false);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);

        // Mientras baja o carga el video, se ve la foto fija de siempre
        ImageView foto = new ImageView(this);
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

        estado = texto(18, 0xEEFFFFFF);
        raiz.addView(estado, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        credito = texto(10, 0x99FFFFFF);
        credito.setText(noche ? CREDITO_NOCHE : CREDITO_DIA);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        lp.setMargins(0, 0, dp(12), dp(10));
        raiz.addView(credito, lp);

        setContentView(raiz);

        File archivo = new File(getFilesDir(), noche ? "mar_noche.webm" : "mar_dia.webm");
        if (archivo.exists()) {
            reproducir(archivo, foto);
        } else {
            estado.setText("Bajando el mar… (solo la primera vez)");
            bajar(noche ? VIDEO_NOCHE : VIDEO_DIA, archivo, foto);
        }
    }

    private void bajar(final String url, final File destino, final ImageView foto) {
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
                        if (cerrando) return; // si sale a mitad, se retoma de cero la próxima
                        out.write(buf, 0, n);
                        llevo += n;
                        final int pct = total > 0 ? (int) (llevo * 100 / total) : 0;
                        if (pct != ultimo) {
                            ultimo = pct;
                            runOnUiThread(() -> estado.setText("Bajando el mar… " + pct + "%\n(solo la primera vez)"));
                        }
                    }
                } finally {
                    c.disconnect();
                }
                if (tmp.renameTo(destino)) runOnUiThread(() -> reproducir(destino, foto));
            } catch (Exception e) {
                tmp.delete();
                runOnUiThread(() -> estado.setText("No pude bajar el video. Revisá internet y probá de nuevo."));
            }
        }).start();
    }

    private void reproducir(File archivo, final ImageView foto) {
        estado.setText("");
        video.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            subirVolumen(mp, 0);
            foto.animate().alpha(0f).setDuration(1500).start();
        });
        video.setOnErrorListener((mp, que, extra) -> {
            estado.setText("Esta tele no pudo reproducir el video");
            return true;
        });
        video.setVideoPath(archivo.getPath());
        video.start();
        // El crédito se ve unos segundos y se va
        handler.postDelayed(() -> credito.animate().alpha(0f).setDuration(2000).start(), 8000);
    }

    /** Las olas entran de a poco, no de golpe. */
    private void subirVolumen(final MediaPlayer mp, final int ms) {
        if (cerrando) return;
        float v = Math.min(1f, ms / (float) SUBIDA_VOLUMEN_MS);
        try {
            mp.setVolume(v, v);
        } catch (Exception e) {
            return;
        }
        if (v < 1f) handler.postDelayed(() -> subirVolumen(mp, ms + 100), 100);
    }

    @Override
    public boolean onKeyDown(int codigo, KeyEvent e) {
        // El volumen y el silencio funcionan normal; cualquier otra tecla vuelve al inicio
        if (codigo == KeyEvent.KEYCODE_VOLUME_UP || codigo == KeyEvent.KEYCODE_VOLUME_DOWN
                || codigo == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(codigo, e);
        }
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Si se va a otra app o se apaga la pantalla, el mar se termina
        finish();
    }

    @Override
    protected void onDestroy() {
        cerrando = true;
        handler.removeCallbacksAndMessages(null);
        video.stopPlayback();
        super.onDestroy();
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
