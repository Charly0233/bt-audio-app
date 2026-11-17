package com.ejemplo.volumenmicrobit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Lee el nivel de volumen multimedia del celular y lo manda a una micro:bit
 * por Bluetooth (servicio UART), como porcentaje 0-100 seguido de un salto de línea.
 */
@SuppressLint("MissingPermission") // los permisos se piden en tiempo de ejecución antes de usar Bluetooth
public class MainActivity extends Activity {

    // Servicio UART de la micro:bit
    private static final UUID UART_SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_CHAR_A = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_CHAR_B = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    private static final int REQ_PERMISOS = 1;
    private static final long TIEMPO_BUSQUEDA_MS = 10000;
    private static final long INTERVALO_LECTURA_MS = 200;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private AudioManager audioManager;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic caracteristicaEscritura;

    private boolean buscando = false;
    private boolean escribiendo = false;
    private long inicioEscritura = 0;
    private int ultimoEnviado = -1;

    private TextView textoEstado;
    private TextView textoVolumen;
    private Button botonConectar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        construirPantalla();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        if (adapter == null) {
            estado("Este celular no tiene Bluetooth.");
            botonConectar.setEnabled(false);
        }

        handler.post(lecturaVolumen);
    }

    private void construirPantalla() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, p);

        textoVolumen = new TextView(this);
        textoVolumen.setTextSize(40);
        textoVolumen.setGravity(Gravity.CENTER);

        textoEstado = new TextView(this);
        textoEstado.setTextSize(18);
        textoEstado.setGravity(Gravity.CENTER);
        textoEstado.setPadding(0, p, 0, p);
        textoEstado.setText("Desconectado");

        botonConectar = new Button(this);
        botonConectar.setText("Conectar a micro:bit");
        botonConectar.setOnClickListener(v -> alPresionarConectar());

        TextView ayuda = new TextView(this);
        ayuda.setGravity(Gravity.CENTER);
        ayuda.setText("Usa los botones de volumen del celular.\nEl valor se manda solo a la micro:bit.");

        layout.addView(textoVolumen);
        layout.addView(textoEstado);
        layout.addView(botonConectar);
        layout.addView(ayuda);
        setContentView(layout);
    }

    // ---------- Volumen ----------

    private int porcentajeVolumen() {
        int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (maximo <= 0) return 0;
        return Math.round(actual * 100f / maximo);
    }

    private final Runnable lecturaVolumen = new Runnable() {
        @Override
        public void run() {
            int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int pct = porcentajeVolumen();
            textoVolumen.setText(pct + "%\n(" + actual + " de " + maximo + ")");

            // Si una escritura se quedó colgada más de 1 s, la liberamos
            if (escribiendo && System.currentTimeMillis() - inicioEscritura > 1000) {
                escribiendo = false;
            }
            if (caracteristicaEscritura != null && !escribiendo && pct != ultimoEnviado) {
                enviar(pct);
            }
            handler.postDelayed(this, INTERVALO_LECTURA_MS);
        }
    };

    private void enviar(int valor) {
        if (gatt == null || caracteristicaEscritura == null) return;
        byte[] datos = (valor + "\n").getBytes(StandardCharsets.US_ASCII);
        int props = caracteristicaEscritura.getProperties();
        int tipo = (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        caracteristicaEscritura.setWriteType(tipo);
        caracteristicaEscritura.setValue(datos);
        escribiendo = true;
        inicioEscritura = System.currentTimeMillis();
        if (gatt.writeCharacteristic(caracteristicaEscritura)) {
            ultimoEnviado = valor;
        } else {
            escribiendo = false;
        }
    }

    // ---------- Permisos ----------

    private String[] permisosNecesarios() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean tienePermisos() {
        for (String p : permisosNecesarios()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void alPresionarConectar() {
        if (gatt != null) {
            desconectar();
            return;
        }
        if (!tienePermisos()) {
            requestPermissions(permisosNecesarios(), REQ_PERMISOS);
            return;
        }
        if (!adapter.isEnabled()) {
            estado("Prende el Bluetooth y vuelve a intentar.");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        buscar();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISOS) return;
        if (tienePermisos()) {
            alPresionarConectar();
        } else {
            estado("Sin permiso de Bluetooth no se puede conectar.");
        }
    }

    // ---------- Búsqueda y conexión ----------

    private void buscar() {
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            estado("No se pudo iniciar la búsqueda Bluetooth.");
            return;
        }
        buscando = true;
        estado("Buscando micro:bit...");
        botonConectar.setEnabled(false);
        scanner.startScan(callbackBusqueda);
        handler.postDelayed(() -> {
            if (buscando) {
                detenerBusqueda();
                estado("No encontré ninguna micro:bit.\nRevisa que tenga el programa cargado.");
            }
        }, TIEMPO_BUSQUEDA_MS);
    }

    private void detenerBusqueda() {
        if (buscando && scanner != null) {
            scanner.stopScan(callbackBusqueda);
        }
        buscando = false;
        botonConectar.setEnabled(true);
    }

    private final ScanCallback callbackBusqueda = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!buscando) return;
            String nombre = null;
            if (result.getScanRecord() != null) nombre = result.getScanRecord().getDeviceName();
            if (nombre == null) nombre = result.getDevice().getName();
            if (nombre != null && nombre.startsWith("BBC micro:bit")) {
                detenerBusqueda();
                conectar(result.getDevice(), nombre);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            buscando = false;
            botonConectar.setEnabled(true);
            estado("Falló la búsqueda (error " + errorCode + ").");
        }
    };

    private void conectar(BluetoothDevice dispositivo, String nombre) {
        estado("Conectando a " + nombre + "...");
        botonConectar.setEnabled(false);
        gatt = dispositivo.connectGatt(this, false, callbackGatt, BluetoothDevice.TRANSPORT_LE);
    }

    private void desconectar() {
        caracteristicaEscritura = null;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        escribiendo = false;
        estado("Desconectado");
        botonConectar.setText("Conectar a micro:bit");
        botonConectar.setEnabled(true);
    }

    private final BluetoothGattCallback callbackGatt = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> estado("Conectado, buscando servicio UART..."));
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> desconectar());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService servicio = g.getService(UART_SERVICE);
            BluetoothGattCharacteristic c = null;
            if (servicio != null) {
                c = escribible(servicio.getCharacteristic(UART_CHAR_A));
                if (c == null) c = escribible(servicio.getCharacteristic(UART_CHAR_B));
            }
            final BluetoothGattCharacteristic encontrada = c;
            runOnUiThread(() -> {
                if (encontrada == null) {
                    estado("La micro:bit no tiene el servicio UART.\nCarga el programa de MakeCode.");
                    desconectar();
                    return;
                }
                caracteristicaEscritura = encontrada;
                ultimoEnviado = -1; // fuerza un envío inmediato
                estado("Conectado ✔");
                botonConectar.setText("Desconectar");
                botonConectar.setEnabled(true);
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            runOnUiThread(() -> {
                escribiendo = false;
                if (status != BluetoothGatt.GATT_SUCCESS) ultimoEnviado = -1; // reintenta
            });
        }
    };

    private static BluetoothGattCharacteristic escribible(BluetoothGattCharacteristic c) {
        if (c == null) return null;
        int props = c.getProperties();
        boolean ok = (props & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
        return ok ? c : null;
    }

    private void estado(String texto) {
        textoEstado.setText(texto);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        detenerBusqueda();
        desconectar();
        super.onDestroy();
    }
}
