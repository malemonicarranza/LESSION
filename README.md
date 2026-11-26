# LESSION

## Sesiones EMG

Desde **Conexión**, iniciá una sesión antes o durante la recepción BLE y finalizala
para guardar su fecha, duración y cantidad de muestras. Las mediciones reales
(`t_ms` y ADC) se almacenan localmente con Room. La pestaña **Sesiones** permite
consultar el historial y abrir el gráfico de cada sesión; no se envían los datos
a un servidor.

## Diagnostico BLE

El firmware escribe en el monitor serie a 115200 baudios. `[BLE_TX_STATUS]`
muestra cada 5 segundos si el cliente esta conectado, si TX tiene CCCD activo,
si START permite enviar y cuantos `notify()` se solicitaron. `[BLE_NOTIFY]`
registra el contador y la frecuencia aproximada de solicitudes; no confirma que
Android las haya recibido.

En Android, filtra Logcat por `LessionBle`. `[GATT_SUBSCRIPTION]` confirma la
suscripcion local y la escritura del CCCD, `[BLE_NOTIFY_MONITOR]` informa si
llegan callbacks TX y su frecuencia, y `[BLE_PARSE]` muestra las muestras
decodificadas. Si el contador del firmware avanza pero no hay callbacks en
Android, la falla esta entre el envio BLE y la recepcion GATT; si hay callbacks
pero no parseo, revisa el contenido y formato del payload.