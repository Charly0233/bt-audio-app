// Programa para la micro:bit (MakeCode, pestaña JavaScript).
// Antes: agrega la extensión "bluetooth" y en Configuración del proyecto
// elige "No Pairing Required" (sin emparejamiento).

let volumen = 0

bluetooth.startUartService()
basic.showIcon(IconNames.Heart)

bluetooth.onBluetoothConnected(function () {
    basic.showIcon(IconNames.Yes)
})

bluetooth.onBluetoothDisconnected(function () {
    basic.showIcon(IconNames.No)
})

// Llega un número de 0 a 100 por cada cambio de volumen del celular
bluetooth.onUartDataReceived(serial.delimiters(Delimiters.NewLine), function () {
    volumen = parseFloat(bluetooth.uartReadUntil(serial.delimiters(Delimiters.NewLine)))
    led.plotBarGraph(volumen, 100)
})

// Botón A: muestra el porcentaje exacto
input.onButtonPressed(Button.A, function () {
    basic.showNumber(volumen)
    led.plotBarGraph(volumen, 100)
})
