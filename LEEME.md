# Volumen micro:bit

App de Android que lee el nivel de volumen del celular (multimedia) y lo manda
a la micro:bit por Bluetooth como un porcentaje de 0 a 100.

## 1. Preparar la micro:bit
1. Entra a https://makecode.microbit.org y crea un proyecto nuevo.
2. Extensiones → busca **bluetooth** y agrégala (te avisará que quita "radio": acepta).
3. Engrane ⚙ → **Configuración del proyecto** → elige **No Pairing Required** → Guardar.
4. Pestaña **JavaScript**: pega el contenido de `microbit/volumen-microbit.js`.
5. Descarga el .hex a la micro:bit. Debe mostrar un corazón.

## 2. Instalar la app
1. Instala Android Studio (gratis): https://developer.android.com/studio
2. File → Open → elige la carpeta `VolumenMicrobit`. Espera a que termine de sincronizar.
3. En el celular activa **Opciones de desarrollador** (toca 7 veces "Número de compilación"
   en Ajustes → Acerca del teléfono) y luego **Depuración USB**.
4. Conecta el celular por USB y presiona ▶ Run. La app se instala sola.

## 3. Usarla
1. Abre la app y toca **Conectar a micro:bit** (acepta el permiso de Bluetooth).
2. Cuando diga "Conectado ✔", la micro:bit muestra una palomita.
3. Sube o baja el volumen con los botones del celular: la micro:bit dibuja una barra.
   El botón A muestra el número exacto.

## Problemas comunes
- **No encuentra la micro:bit**: revisa que el programa tenga el bloque
  `bluetooth iniciar servicio UART` y que no esté conectada a otro celular o a la app oficial.
- **Pide emparejar o se desconecta**: falta poner "No Pairing Required" en el paso 1.3.
- Funciona con micro:bit V1 y V2, y con Android 6 o más nuevo.
