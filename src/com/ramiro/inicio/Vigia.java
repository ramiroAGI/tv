package com.ramiro.inicio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

/**
 * Cuando la tele muestra su pantalla de inicio original (botón Inicio, salir de una app,
 * despertar del reposo), abre la nuestra encima. Solo escucha a esa pantalla, nada más.
 * Se apaga desde Ajustes → Accesibilidad.
 */
public class Vigia extends AccessibilityService {

    private String inicioOriginalPaquete;
    private String inicioOriginalClase;

    @Override
    protected void onServiceConnected() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PackageManager pm = getPackageManager();
        // Primero la que la tele tiene elegida como inicio; si no, la de mayor prioridad que no seamos nosotros
        ResolveInfo elegida = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (elegida != null && esOtroInicio(elegida)) {
            inicioOriginalPaquete = elegida.activityInfo.packageName;
            inicioOriginalClase = elegida.activityInfo.name;
        } else {
            for (ResolveInfo r : pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (!esOtroInicio(r)) continue;
                inicioOriginalPaquete = r.activityInfo.packageName;
                inicioOriginalClase = r.activityInfo.name;
                break;
            }
        }
        if (inicioOriginalPaquete == null) return;

        // Escuchar solo a la app de inicio original
        AccessibilityServiceInfo info = getServiceInfo();
        info.packageNames = new String[]{inicioOriginalPaquete};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || e.getClassName() == null) return;
        // Solo la pantalla principal: "Aplicaciones", ajustes y demás pantallas siguen funcionando
        if (!e.getPackageName().toString().equals(inicioOriginalPaquete)) return;
        if (!e.getClassName().toString().equals(inicioOriginalClase)) return;

        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception ignored) {
        }
    }

    /** Descarta nuestra app y el selector de Android ("¿Con qué app abrir?"). */
    private boolean esOtroInicio(ResolveInfo r) {
        String p = r.activityInfo.packageName;
        return !p.equals(getPackageName()) && !p.equals("android");
    }

    @Override
    public void onInterrupt() {
    }
}
