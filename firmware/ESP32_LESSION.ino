/*
  ESP32 LESSION - EMG V3 por Bluetooth Low Energy (BLE)

  ARDUINO IDE
  - Gestor de placas: instalar "esp32 by Espressif Systems".
  - Placa a buscar y seleccionar: "ESP32C3 Dev Module".
  - Este sketch usa la API BLE de Arduino-ESP32 3.x.
  - USB CDC On Boot: Enabled, si se utiliza el USB nativo para el monitor.
  - Monitor serie: 115200 baudios. No espera a que se abra el monitor.
  - Bibliotecas: Arduino.h, BLEDevice.h, BLEServer.h y BLE2902.h.
    Todas vienen incluidas en el paquete de placas de Espressif.
    No instalar una biblioteca BLE externa para este sketch.
  - atomic es una cabecera de C++ incluida con el compilador.

  PINES DEL DIAGRAMA
  - Rojo del RGB: GPIO10, mediante su resistencia.
  - Verde del RGB: GPIO9, mediante su resistencia.
  - Azul del RGB: GPIO8, mediante su resistencia.
  - EMG: A0 = GPIO0 en la placa seleccionada.
  - Todas las masas (GND) deben estar unidas como indica el diagrama.
  - RGB de CATODO COMUN por defecto: COM a GND.
    Para ANODO COMUN: COM a 3V3 y cambiar RGB_ANODO_COMUN a true.
    Es un RGB convencional de cuatro terminales, no un LED direccionable.
  - GPIO8 y GPIO9 intervienen en el arranque del ESP32-C3. Si no arranca
    con el RGB conectado, revisar su carga sobre estos pines al encender.

  ALIMENTACION Y ENTRADA ANALOGICA
  - B1 -> interruptor -> elevador ajustado a 5 V -> pin 5V de la placa.
  - No aplicar una celda 18650 directamente al pin 3V3.
  - Este codigo presupone el divisor propuesto en el diagrama:
      EMG SIG -> resistencia 10 kohm -> A0
      A0      -> resistencia 10 kohm -> GND
    Con SIG de 4,2 V, A0 recibe aproximadamente 2,1 V.
  - Resolucion de lectura: 12 bits, de 0 a 4095.
  - Atenuacion ADC_11db: rango util del C3 de aproximadamente 0 a 2,5 V.
    Los datos enviados son cuentas ADC; NO son milivoltios ni fuerza muscular.
  - EMG V3 entrega una envolvente rectificada y suavizada, no EMG crudo.
  - Con electrodos en el cuerpo, utilizar solo baterias, sin USB/cargadores.

  COLORES / ESTADOS
  - ROJO: encendido, sin cliente BLE conectado (tambien al perder conexion).
  - AZUL: BLE conectado, pero sin transmision activa.
  - VERDE: adquisicion y envio de notificaciones activos.
  IMPORTANTE: verde NO confirma contacto de electrodos ni presencia fisica
  del sensor. EMG V3 no tiene salida de deteccion de electrodo desconectado.
  Una entrada flotante o un musculo en reposo no permiten distinguir esos
  casos de forma fiable usando solo A0. Verificar los electrodos manualmente.

  CONFIGURACION DE LA APP MOVIL RECEPTORA
  - Es BLE/GATT, no Bluetooth clasico SPP. Un unico cliente a la vez.
  - Nombre exacto del periferico: ESP32 LESSION
  - Servicio (UART): 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
  - Caracteristica TX, ESP32 -> app (READ + NOTIFY):
      6E400003-B5A3-F393-E0A9-E50E24DCCA9E
  - Caracteristica RX, app -> ESP32 (WRITE):
      6E400002-B5A3-F393-E0A9-E50E24DCCA9E
  - Despues de conectar, descubrir el servicio y suscribirse a TX.
    Habilitar notificaciones con el mecanismo de la plataforma; escribir
    01 00 en el descriptor CCCD 0x2902 cuando la API lo requiera.
    Conectar solamente no inicia el envio: es necesario suscribirse a TX.
  - El nombre puede venir en la respuesta de escaneo; realizar escaneo activo.
  - Tras reconectar, volver a descubrir y suscribirse. No hay emparejamiento
    obligatorio, contrasena ni cifrado configurados en este ejemplo.

  PAQUETES ENVIADOS / PROTOCOLO VERSION 1
  - Una muestra por notificacion, 50 muestras/segundo (periodo nominal 20 ms).
  - Texto ASCII, compatible UTF-8. Sin cabecera, sin espacios y sin JSON.
  - Formato exacto: t_ms,adc\n
      t_ms: entero decimal sin signo, milisegundos desde el arranque.
            Es uint32_t y vuelve a 0 aproximadamente cada 49,7 dias.
      adc: entero decimal de 0 a 4095, promedio de cuatro lecturas de A0.
      Separador: coma ASCII 0x2C.
      Fin de registro: LF 0x0A. No se envia CR (0x0D).
  - Ejemplo literal recibido: 12340,2048 seguido del byte 0A.
    En notacion escapada: "12340,2048\n".
    Bytes hexadecimales: 31 32 33 34 30 2C 32 30 34 38 0A.
  - Longitud variable de 4 a 16 bytes. Cabe en los 20 bytes de una
    notificacion con MTU por defecto de 23; no requiere negociar otro MTU.
  - La app acumula bytes hasta LF, separa la linea por coma y convierte
    ambos campos a enteros. Descartar lineas incompletas o mal formadas.
  - El tiempo corresponde al comienzo de las cuatro lecturas promediadas.
  - No se recuperan muestras perdidas: NOTIFY no tiene confirmacion GATT.
    El periodo es nominal; BLE y otras tareas pueden introducir variaciones.
  - Al dejar de transmitir, TX conserva la ultima muestra (READ no significa
    que sea nueva). Al reiniciar el dispositivo, t_ms vuelve a cero.

  COMANDOS OPCIONALES APP -> RX
  - Escribir texto ASCII "STOP": pausa el envio, LED azul.
  - Escribir texto ASCII "START": reanuda si la app sigue suscrita a TX.
  - Se aceptan minusculas y un final CR/LF opcional. Sin respuesta/ACK.
  - En cada conexion, START queda habilitado automaticamente.
    Basta suscribirse; no es obligatorio enviar START.
  - Se mantiene AZUL durante el primer segundo de cada conexion, para
    mostrar el enlace establecido; luego comienza el envio si hay suscripcion.

  Referencias de API:
  https://docs.espressif.com/projects/arduino-esp32/en/latest/api/ble.html
  https://docs.espressif.com/projects/arduino-esp32/en/latest/api/adc.html
*/

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <atomic>

constexpr uint8_t PIN_ROJO = 10;
constexpr uint8_t PIN_VERDE = 9;
constexpr uint8_t PIN_AZUL = 8;
constexpr uint8_t PIN_EMG = A0;  // GPIO0 en ESP32C3 Dev Module.
constexpr bool RGB_ANODO_COMUN = false;
constexpr uint32_t PERIODO_MS = 20;  // 50 Hz, envolvente EMG V3.
constexpr uint32_t AZUL_INICIAL_MS = 1000;

constexpr char NOMBRE_BLE[] = "ESP32 LESSION";
constexpr char UUID_SERVICIO[] = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
constexpr char UUID_TX[] = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
constexpr char UUID_RX[] = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";

BLECharacteristic *caracteristicaTX = nullptr;
BLE2902 *cccdTX = nullptr;
std::atomic<bool> conectado{false};
std::atomic<bool> envioHabilitado{true};
std::atomic<bool> desconexionPendiente{false};
std::atomic<uint32_t> instanteConexion{0};

void colorLED(bool rojo, bool verde, bool azul) {
  digitalWrite(PIN_ROJO, (rojo != RGB_ANODO_COMUN) ? HIGH : LOW);
  digitalWrite(PIN_VERDE, (verde != RGB_ANODO_COMUN) ? HIGH : LOW);
  digitalWrite(PIN_AZUL, (azul != RGB_ANODO_COMUN) ? HIGH : LOW);
}

class EventosServidor : public BLEServerCallbacks {
  void onConnect(BLEServer *servidor) override {
    (void)servidor;
    envioHabilitado.store(true);
    instanteConexion.store(millis());
    conectado.store(true);
    Serial.printf("[BLE_CONN] Cliente conectado; START habilitado, esperando suscripcion TX y %lu ms iniciales.\n",
      static_cast<unsigned long>(AZUL_INICIAL_MS));
  }

  void onDisconnect(BLEServer *servidor) override {
    (void)servidor;
    conectado.store(false);
    // No reutilizar la suscripcion de un cliente que ya se desconecto.
    cccdTX->setNotifications(false);
    desconexionPendiente.store(true);
    Serial.println("[BLE_CONN] Cliente desconectado; notificaciones TX deshabilitadas.");
  }
};

class EventosRX : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *caracteristica) override {
    String comando = caracteristica->getValue();
    comando.trim();
    comando.toUpperCase();
    if (comando == "STOP") {
      envioHabilitado.store(false);
      Serial.println("[BLE_RX] Comando STOP; envio pausado.");
    } else if (comando == "START") {
      envioHabilitado.store(true);
      Serial.println("[BLE_RX] Comando START; envio habilitado si TX esta suscripta.");
    } else {
      Serial.printf("[BLE_RX] Comando desconocido: \"%s\".\n", comando.c_str());
    }
  }
};

void setup() {
  // Cargar el nivel apagado antes de configurar los GPIO como salidas.
  digitalWrite(PIN_ROJO, RGB_ANODO_COMUN ? HIGH : LOW);
  digitalWrite(PIN_VERDE, RGB_ANODO_COMUN ? HIGH : LOW);
  digitalWrite(PIN_AZUL, RGB_ANODO_COMUN ? HIGH : LOW);
  pinMode(PIN_ROJO, OUTPUT);
  pinMode(PIN_VERDE, OUTPUT);
  pinMode(PIN_AZUL, OUTPUT);
  colorLED(true, false, false);

  Serial.begin(115200);
  pinMode(PIN_EMG, INPUT);  // Sin pull-up ni pull-down interno.
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_EMG, ADC_11db);

  BLEDevice::init(NOMBRE_BLE);
  BLEServer *servidor = BLEDevice::createServer();
  servidor->setCallbacks(new EventosServidor());
  BLEService *servicio = servidor->createService(UUID_SERVICIO);

  caracteristicaTX = servicio->createCharacteristic(
    UUID_TX, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  cccdTX = new BLE2902();
  caracteristicaTX->addDescriptor(cccdTX);
  caracteristicaTX->setValue("");  // Sin muestra hasta el primer envio.

  BLECharacteristic *rx = servicio->createCharacteristic(
    UUID_RX, BLECharacteristic::PROPERTY_WRITE);
  rx->setCallbacks(new EventosRX());
  servicio->start();

  BLEAdvertising *publicidad = BLEDevice::getAdvertising();
  publicidad->addServiceUUID(UUID_SERVICIO);
  publicidad->setScanResponse(true);  // Nombre completo en scan response.
  publicidad->start();
  Serial.println("ESP32 LESSION listo. Rojo: esperando BLE.");
  Serial.printf("[BLE_CONFIG] Servicio=%s TX=%s periodo_nominal_ms=%lu; requiere cliente, CCCD TX y espera inicial.\n",
    UUID_SERVICIO, UUID_TX, static_cast<unsigned long>(PERIODO_MS));
}

void loop() {
  static uint32_t ultimaMuestra = 0;
  static uint32_t ultimoEnvio = 0;
  static uint32_t instanteDesconexion = 0;
  static uint32_t ultimoDiagnostico = 0;
  static uint32_t ultimoLogEnvio = 0;
  static uint32_t totalNotificacionesSolicitadas = 0;
  static uint32_t notificacionesAlUltimoLog = 0;
  static bool huboEnvio = false;
  static bool reiniciarPublicidad = false;
  static bool estadoTransmisionAnterior = false;
  const uint32_t ahora = millis();

  if (desconexionPendiente.exchange(false)) {
    instanteDesconexion = ahora;
    reiniciarPublicidad = true;
    huboEnvio = false;
  }

  if (!conectado.load()) {
    colorLED(true, false, false);
    huboEnvio = false;
    // Dar tiempo al stack BLE sin bloquear con una espera larga.
    if (reiniciarPublicidad && uint32_t(ahora - instanteDesconexion) >= 250) {
      BLEDevice::startAdvertising();
      reiniciarPublicidad = false;
    }
    delay(1);
    return;
  }

  reiniciarPublicidad = false;
  const bool cccdHabilitado = cccdTX->getNotifications();
  const bool comandoHabilitado = envioHabilitado.load();
  const bool esperaInicialCumplida =
    uint32_t(ahora - instanteConexion.load()) >= AZUL_INICIAL_MS;
  const bool transmitir = comandoHabilitado && cccdHabilitado && esperaInicialCumplida;

  if (transmitir != estadoTransmisionAnterior) {
    Serial.printf("[BLE_TX_STATE] transmitir=%s conectado=%s START=%s CCCD_TX=%s espera_inicial=%s t_ms=%lu\n",
      transmitir ? "si" : "no",
      conectado.load() ? "si" : "no",
      comandoHabilitado ? "si" : "no",
      cccdHabilitado ? "si" : "no",
      esperaInicialCumplida ? "cumplida" : "pendiente",
      static_cast<unsigned long>(ahora));
    estadoTransmisionAnterior = transmitir;
  }

  if (uint32_t(ahora - ultimoDiagnostico) >= 5000) {
    ultimoDiagnostico = ahora;
    Serial.printf("[BLE_TX_STATUS] conectado=%s START=%s CCCD_TX=%s espera_inicial=%s transmitir=%s notify_solicitados=%lu ultimo_envio_hace_ms=%lu\n",
      conectado.load() ? "si" : "no",
      comandoHabilitado ? "si" : "no",
      cccdHabilitado ? "si" : "no",
      esperaInicialCumplida ? "si" : "no",
      transmitir ? "si" : "no",
      static_cast<unsigned long>(totalNotificacionesSolicitadas),
      huboEnvio ? static_cast<unsigned long>(ahora - ultimoEnvio) : 0UL);
  }

  if (!transmitir) {
    colorLED(false, false, true);
    huboEnvio = false;
    delay(1);
    return;
  }

  if (uint32_t(ahora - ultimaMuestra) >= PERIODO_MS) {
    ultimaMuestra = ahora;
    uint32_t suma = 0;
    for (uint8_t i = 0; i < 4; ++i) suma += analogRead(PIN_EMG);
    const uint16_t adc = (suma + 2) / 4;

    char paquete[20];  // Hasta 16 caracteres de datos + terminador NUL.
    const int longitud = snprintf(paquete, sizeof(paquete), "%lu,%u\n",
      static_cast<unsigned long>(ahora), static_cast<unsigned int>(adc));

    // Nunca enviar el NUL de C; solo los caracteres hasta LF inclusive.
    if (longitud > 0 && longitud < static_cast<int>(sizeof(paquete))
        && conectado.load() && envioHabilitado.load() && cccdTX->getNotifications()) {
      caracteristicaTX->setValue(reinterpret_cast<uint8_t *>(paquete), longitud);
      caracteristicaTX->notify();
        ++totalNotificacionesSolicitadas;
        if (totalNotificacionesSolicitadas == 1 ||
            uint32_t(ahora - ultimoLogEnvio) >= 1000) {
          const uint32_t intervaloLog = ultimoLogEnvio == 0 ? 0 : ahora - ultimoLogEnvio;
          const uint32_t notificacionesEnIntervalo =
            totalNotificacionesSolicitadas - notificacionesAlUltimoLog;
          const float frecuenciaHz = intervaloLog == 0
            ? 0.0f
            : static_cast<float>(notificacionesEnIntervalo) * 1000.0f
              / static_cast<float>(intervaloLog);
          Serial.printf("[BLE_NOTIFY] notify() invocado n=%lu t_ms=%lu adc=%u bytes=%d intervalo_log_ms=%lu frecuencia_aprox_hz=%.1f; recepcion de app no confirmada\n",
            static_cast<unsigned long>(totalNotificacionesSolicitadas),
            static_cast<unsigned long>(ahora),
            static_cast<unsigned int>(adc),
            longitud,
            static_cast<unsigned long>(intervaloLog),
            frecuenciaHz);
          ultimoLogEnvio = ahora;
          notificacionesAlUltimoLog = totalNotificacionesSolicitadas;
        }
        ultimoEnvio = ahora;
        huboEnvio = true;
    }
  }

  // Indica envio solicitado al stack, no recepcion confirmada por la app.
  const bool activo = conectado.load() && envioHabilitado.load()
    && cccdTX->getNotifications() && huboEnvio && uint32_t(ahora - ultimoEnvio) < 500;
  if (!conectado.load()) colorLED(true, false, false);
  else if (activo) colorLED(false, true, false);
  else colorLED(false, false, true);
  delay(1);
}
