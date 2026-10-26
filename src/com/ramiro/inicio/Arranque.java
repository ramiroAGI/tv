package com.ramiro.inicio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Abre la pantalla de inicio cuando la tele termina de arrancar. */
public class Arranque extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // En algunas versiones de Android esto está bloqueado; si la app es el inicio, no hace falta.
        }
    }
}
